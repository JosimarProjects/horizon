# Horizon

Baixador de vídeos para Android — **sem anúncios**, sem telemetria, sem compras.
Cole ou compartilhe um link do YouTube, Instagram, Facebook, TikTok ou X e o
vídeo é baixado direto no aparelho.

Projeto pessoal, reconstruído do zero (clean-room) a partir da engenharia
reversa de um baixador comercial cheio de anúncios. Nenhuma linha, imagem ou
texto do app original foi copiada — só a **funcionalidade e o fluxo** foram
recriados, e toda a camada de anúncios/telemetria/IAP foi removida.

A extração usa o **yt-dlp rodando no próprio aparelho**, via
[`youtubedl-android`](https://github.com/junkfood02/youtubedl-android) — o mesmo
motor (yt-dlp) que baixadores comerciais usam em servidores, só que local, sem
depender de backend nenhum.

## Funcionalidades

**Baixar**
- Compartilhar → Horizon ou copiar o link e abrir o app
- Fila incremental (vários links, um a um)
- Renomear o arquivo e escolher a pasta no momento do download
- Qualidade/compressão com **estimativa de tamanho** (original × comprimido)

**Organizar**
- Pastas (criar, renomear, excluir, mover) e categorias (tags) independentes
- Playlists que tocam em sequência
- Ordenação, seleção múltipla, capas por categoria/pasta/lista
- Uso de espaço (vídeos + livre no aparelho)

**Assistir** (vitrine estilo Netflix)
- Hero + fileiras (Continuar assistindo, Histórico, Recentes, por categoria/pasta/playlist)
- Player interno (ExoPlayer/Media3): velocidade 0.25–3×, retomar de onde parou
- **Áudio em segundo plano / tela bloqueada** e **Picture-in-Picture**
- **Enviar para TV (Cast)** — vídeo e áudio só na TV
- Marcar/desmarcar assistido

> Arquitetura, componentes e fluxos detalhados em [ARCHITECTURE.md](ARCHITECTURE.md).

## Stack

- Kotlin, Views + Material 3
- [youtubedl-android](https://github.com/junkfood02/youtubedl-android) (yt-dlp + ffmpeg + aria2c)
- AndroidX Media3 (ExoPlayer + MediaSession) para reprodução e áudio em background
- Google Cast + NanoHTTPD (enviar para TV) · Coil (thumbnails)

## Build

```bash
./gradlew assembleDebug
# APK em app/build/outputs/apk/debug/app-debug.apk
```

Requer Android SDK (compileSdk 34). ABIs: arm64-v8a e armeabi-v7a.

## Licença

GPL-3.0 — herdada do `youtubedl-android`/yt-dlp. Veja [LICENSE](LICENSE).

> Uso pessoal. Respeite os termos de serviço das plataformas e os direitos
> autorais do conteúdo que você baixar.
