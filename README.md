# LiveCraft Android — protótipo 0.1.0

Aplicativo Android inicial para conectar um mundo Minecraft Bedrock local a um servidor WebSocket executado no próprio celular.

## Estado atual
- Interface Android inicial incluída.
- Servidor WebSocket local em 127.0.0.1:8765.
- Botão de teste para enviar `summon zombie ~ ~1 ~`.
- Integração com TikTok/PirateTok ainda não incluída.
- Este projeto ainda não foi validado em um aparelho real.

## Teste pretendido
1. Abrir o LiveCraft e iniciar a conexão local.
2. Abrir um mundo próprio do Bedrock com cheats ativados.
3. Se disponível, habilitar WebSockets nas configurações do Minecraft.
4. No chat, executar `/connect 127.0.0.1:8765`.
5. Voltar ao LiveCraft e tocar em “Testar: invocar 1 zumbi”.

## Limitações
A conexão precisa ser testada na versão exata do Minecraft e do Android. Há relatos de desconexão imediata do comando `/connect` em algumas combinações recentes de Bedrock e Android. Se ocorrer, será necessário avaliar uma ponte alternativa baseada em add-on/Script API.

## APK
O workflow em `.github/workflows/android.yml` compila o APK de depuração e publica o arquivo como artefato do GitHub Actions, se a compilação for concluída.
