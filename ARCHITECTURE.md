# Horizon — Arquitetura e Funcionalidades

Baixador de vídeos para Android, **sem anúncios**, com vitrine de streaming
local. Reconstrução clean-room (funcionalidade e fluxo) de um baixador
comercial cheio de anúncios — nenhuma linha, imagem ou texto do original foi
copiada. GPL-3.0 (herdada do yt-dlp/youtubedl-android).

- **applicationId:** `br.com.josimar.horizon`
- **minSdk:** 24 · **compileSdk/targetSdk:** 34 · **versionName:** 1.0
- **Linguagem/UI:** Kotlin, Android Views + Material 3 (sem Compose)

---

## 1. Visão geral

O app tem **duas frentes**:

1. **Baixar** — cola/compartilha um link (YouTube, Instagram, Facebook, TikTok,
   X…), escolhe nome/pasta/qualidade e baixa com **yt-dlp rodando no próprio
   aparelho** (sem backend).
2. **Assistir** — uma **vitrine estilo Netflix** dos vídeos já baixados, com
   categorias, pastas, playlists, histórico, player interno, PiP, áudio em
   segundo plano e envio para a TV (Cast).

O motor de extração é o **yt-dlp** via
[`youtubedl-android`](https://github.com/junkfood02/youtubedl-android) — o mesmo
motor que serviços comerciais usam em servidores, só que local. É mantido
atualizado automaticamente (canal nightly) porque o YouTube quebra extração com
frequência.

---

## 2. Navegação (telas)

```
HomeActivity (launcher)
├─ botão Download  → diálogo (nome + pasta + qualidade/compressão) → DownloadService
├─ seletor de pasta de destino (tocar = trocar/criar · segurar = renomear)
├─ STREAMING → StreamActivity (vitrine)
│   ├─ ícone "gerenciar" → LibraryActivity
│   └─ tocar card → PlayerActivity
├─ VÍDEOS → LibraryActivity (gestão em grade)
│   └─ tocar card → PlayerActivity
├─ (i) → HowToActivity
└─ engrenagem → SettingsActivity (qualidade padrão + uso de espaço)
```

---

## 3. Componentes (Kotlin)

### Telas (Activities)
| Arquivo | Papel |
| --- | --- |
| `HomeActivity` | Entrada. Captura URL de **compartilhar (ACTION_SEND)** e da **área de transferência** (lida em `onWindowFocusChanged`, exigência do Android 10+). Seletor/criar/renomear pasta de destino. **Diálogo de download** (renomear, pasta, qualidade/compressão com estimativas). **Fila incremental** (cola 1, baixa, cola outro) com dedupe. Observa o progresso via `StateFlow`. |
| `StreamActivity` | **Vitrine**: hero + fileiras horizontais (Continuar assistindo, Histórico, Recentes, uma por **categoria/pasta/playlist**). **Chips de filtro** coloridos. **Seleção múltipla** (excluir/mover/compartilhar/categoria/lista em lote). **Capa** por categoria/pasta/lista. Toca lista em sequência. |
| `LibraryActivity` | **Gestão em grade**: chips de pasta, **ordenação**, renomear/mover/excluir, **seleção múltipla**, categoria, adicionar à lista, **marcar assistido**, excluir pasta. |
| `PlayerActivity` | Player (ver §5): velocidade, retomar posição, **PiP**, **áudio em background**, **Cast**, compartilhar. Playlist em sequência. |
| `SettingsActivity` | Qualidade padrão + **uso de espaço** (vídeos + livre no aparelho). |
| `HowToActivity` | Tutorial. |

### Serviços
| Arquivo | Papel |
| --- | --- |
| `DownloadService` | Foreground service (`dataSync`). **Fila** de downloads (`ConcurrentLinkedQueue`) processada em sequência; notificação de progresso; chama o yt-dlp. Modos de qualidade/compressão, nome customizado, template de pasta/playlist. |
| `PlaybackService` | `MediaSessionService` do Media3. Dono do `ExoPlayer`; mantém o **áudio tocando em segundo plano / tela bloqueada** com notificação de mídia. |
| `CastServerService` | Foreground service (`mediaPlayback`) que mantém o **servidor HTTP local vivo** enquanto transmite para a TV — permite fechar o player e usar o celular com a TV tocando. |

### Modelo / dados (objetos + SharedPreferences)
| Arquivo | Papel | Prefs |
| --- | --- | --- |
| `Downloads` | Progresso (`StateFlow<DownloadProgress>`), pastas (criar/renomear com **migração de caminhos**/excluir), `listVideos` com **ordenação**, mover/renomear arquivo, **uso/espaço** (`usedBytes`/`freeBytes`). | `settings` |
| `Catalog` | **Categorias (tags)** por vídeo (independentes da pasta) e **capas** por chave. | `categories`, `covers` |
| `Playlists` | **Listas** (nome → caminhos ordenados em JSON). | `playlists` |
| `Resume` | **Posição** de reprodução, **histórico** e **flag de assistido** (auto ≥95% ou manual). | `resume`, `history`, `watched` |
| `Palette` | **Cor estável por pasta/categoria** (hash do nome). | — |

### Cast / servidor
| Arquivo | Papel |
| --- | --- |
| `CastOptionsProvider` | Configura o Google Cast (receptor de mídia padrão do Chromecast). |
| `LocalMediaServer` | `NanoHTTPD` que serve **um arquivo** com suporte a **Range** (necessário para seek no Cast). |

### Adapters
| Arquivo | Papel |
| --- | --- |
| `StreamRowAdapter` | Cards landscape da vitrine (thumb via Coil, barra de progresso de retomada, selo "assistido", seleção). |
| `VideoAdapter` | Cards da grade de gestão (thumb, ações, seleção, selo "assistido"). |

### Application
`App` — inicializa o motor sob demanda (`ensureEngine`: `YoutubeDL.init` +
`FFmpeg.init`), **atualiza o yt-dlp** (nightly, no máx. 1×/dia), cria o canal de
notificação e registra um **listener global de Cast** para encerrar o
`CastServerService` quando a transmissão termina.

---

## 4. Fluxo de download

1. **Captura da URL**: `ACTION_SEND` (compartilhar) ou área de transferência
   (lida quando a janela ganha foco). Regex extrai **todos** os links do texto.
2. **Diálogo de download**: `yt-dlp getInfo` traz duração e tamanho; o diálogo
   mostra o **tamanho original + estimativas** e deixa **renomear** e **escolher
   a pasta**. Modos: `best` / `720` / `480` / `compress` (re-encode 480p CRF30 via
   `--recode-video` + `--postprocessor-args` do ffmpeg) / `audio` (MP3).
3. **Fila**: cada toque em Download **enfileira** no `DownloadService` (cumulativo,
   sem duplicar), que baixa em sequência mostrando `(x/y)`.
4. **Saída**: `Movies/Horizon/` (armazenamento específico do app — **sem
   permissão**). Pasta escolhida vira subpasta; playlists do yt-dlp vão para uma
   subpasta com o nome da playlist.

---

## 5. Reprodução

- **Player**: `PlayerActivity` usa um `MediaController` (Media3) ligado ao
  `PlaybackService` (`MediaSessionService`) — por isso o **áudio continua em
  segundo plano / com a tela bloqueada**.
- **Voltar** encerra a reprodução; o background só acontece ao **minimizar
  (Home/PiP)** ou **bloquear**.
- **PiP**: ao minimizar, o vídeo vira janela flutuante.
- **Velocidade**: 0.25× a 3×.
- **Retomar**: salva a posição por arquivo (`Resume`) e volta de onde parou;
  marca histórico e "assistido".
- **Playlist**: toca em sequência (auto-avança), retomando por item.
- **Cast (enviar para TV)**: botão no player. Ao conectar numa TV, sobe o
  `CastServerService` (servidor local com Range) e manda o Chromecast reproduzir
  a URL `http://<ip-do-celular>:8099/...`; o player local fica em silêncio
  (**vídeo e áudio só na TV**) e dá pra fechar o player e usar o celular.
  > ⚠️ O Cast precisa de um Chromecast/Google TV na mesma Wi-Fi para validar.

---

## 6. Armazenamento

```
<externalFilesDir>/Movies/Horizon/        ← Principal (raiz)
├─ <vídeos da raiz>.mp4
├─ <Pasta A>/<vídeos>.mp4                  ← pastas criadas pelo usuário
└─ <Playlist do yt-dlp>/NNN-<título>.mp4   ← quando o link é uma playlist
```

Tudo em diretório **específico do app** (não precisa de permissão de
armazenamento). Metadados (categorias, playlists, capas, histórico, assistido,
posição) ficam em `SharedPreferences` — chaveados pelo **caminho** do arquivo
(renomear pasta migra os caminhos nessas prefs).

---

## 7. Dependências principais

- `io.github.junkfood02.youtubedl-android` `library`/`ffmpeg`/`aria2c` 0.18.1 — motor yt-dlp
- `androidx.media3` `exoplayer`/`ui`/`session` 1.4.1 — player + sessão de mídia
- `io.coil-kt` `coil`/`coil-video` 2.7.0 — thumbnails (frame de vídeo)
- `com.google.android.gms:play-services-cast-framework` 21.5.0 + `androidx.mediarouter` 1.7.0 — Cast
- `org.nanohttpd:nanohttpd` 2.3.1 — servidor local para o Cast
- Material 3, AndroidX, Kotlin Coroutines

ABIs empacotadas: `arm64-v8a`, `armeabi-v7a`.

---

## 8. Build

```bash
./gradlew assembleDebug
# APK em app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Requer Android SDK (compileSdk 34). No HyperOS/MIUI, habilite "Instalar via USB"
nas Opções do desenvolvedor.

---

## 9. Permissões (AndroidManifest)

`INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK` (áudio em background),
`POST_NOTIFICATIONS`, `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC`
(download) + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (player/Cast).

---

## 10. Funcionalidades (resumo)

- [x] Baixar de YouTube, Instagram, Facebook, TikTok, X (yt-dlp on-device, auto-update)
- [x] Compartilhar/colar link · fila incremental (vários, um a um)
- [x] Renomear o arquivo e escolher a pasta no momento do download
- [x] Qualidade/compressão com estimativa (original × comprimido)
- [x] Pastas: criar, renomear, excluir, mover vídeos
- [x] Categorias (tags) independentes da pasta
- [x] Playlists que tocam em sequência
- [x] Vitrine (hero + fileiras), filtros coloridos, capas, seleção múltipla
- [x] Gestão em grade: ordenar, renomear, mover, excluir em lote
- [x] Player: velocidade 0.25–3×, retomar, PiP, áudio em background, compartilhar
- [x] Enviar para TV (Cast) — vídeo/áudio só na TV, celular livre *(validar em TV real)*
- [x] Histórico e "Continuar assistindo"
- [x] Marcar/desmarcar assistido
- [x] Uso de espaço (vídeos + livre no aparelho)
- [x] Sem anúncios, sem telemetria, sem backend
