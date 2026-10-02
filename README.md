# telegramBot

El bot conserva la selección inicial `/donador` / `/admin` y los comandos de la entrega 4, incluidos parámetros separados por `|`. La entrega 5 agrega consultas y ABM de los cuatro componentes mediante `/ayuda COMPONENTE`.

Las respuestas se muestran como campos legibles. Para respuestas largas usar `/pagina N` sobre la última consulta, durante diez minutos. No vuelve a ejecutar la operación ni requiere copiar JSON de respuesta.

Opcional: `TELEGRAM_ADMIN_CHAT_IDS=123,456` limita la selección de admin a esos chats. Sin configurar se conserva el selector de rol previsto por la consigna. Para autorizar personas usar chats privados; un grupo identifica al chat, no a cada miembro.

Logs centralizados opcionales: `BETTERSTACK_SOURCE_TOKEN`, `BETTERSTACK_INGEST_URL`, `APP_NAME=donatrack-telegram` e `INSTANCE_ID`. No registrar tokens ni cuerpos de mensajes. Mantener una sola instancia por token.

Configuración y ensayo: [guía de presentación](../testing/SETUP_PRESENTACION.md) y [alcance de mejoras](../testing/MEJORAS_ENTREGA5.md) en el checkout TPA.
