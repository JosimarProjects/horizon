# Horizon

Baixador de vídeos para Android — **sem anúncios**, sem telemetria, sem compras.
Cole ou compartilhe um link do YouTube, Instagram, Facebook, TikTok ou X e o
vídeo é baixado direto no aparelho.

Projeto pessoal, reconstruído do zero (clean-room) a partir da engenharia
reversa de um baixador comercial cheio de anúncios. Nenhuma linha, imagem ou
texto do app original foi copiada — só a **funcionalidade e o fluxo** foram
recriados, e toda a camada de anúncios/telemetria/IAP foi removida.

## Como funciona

A extração usa o **yt-dlp rodando no próprio aparelho**, via
[`youtubedl-android`](https://github.com/junkfood02/youtubedl-android) — o mesmo
motor (yt-dlp) que baixadores comerciais usam em servidores, só que local, sem
depender de backend nenhum.

- **Compartilhar → Horizon** ou copiar o link e abrir o app
- Validação de URL, download com progresso em notificação (foreground service)
- Qualidade selecionável (melhor / 1080p / 720p / só áudio MP3)
- Galeria dos vídeos baixados com player interno (ExoPlayer/Media3)

## Stack

- Kotlin, Views + Material 3
- [youtubedl-android](https://github.com/junkfood02/youtubedl-android) (yt-dlp + ffmpeg)
- AndroidX Media3 (ExoPlayer) para reprodução
- Coil para thumbnails

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
