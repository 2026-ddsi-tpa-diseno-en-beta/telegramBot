# DonaTrack en Telegram

El bot conserva los comandos anteriores y agrega formularios guiados para las 84 operaciones del catálogo compartido con MCP.

- `/start`: elegir Donador o Administrador.
- `/menu`: abrir el menú; `/consultas` muestra los cuatro reportes integrados.
- `/operaciones COMPONENTE`: consultar las operaciones disponibles.
- Una operación sin argumentos abre su formulario. `/omitir` salta campos opcionales y `/atras` permite corregirlos.
- Las escrituras guiadas muestran un resumen y requieren `/confirmar`. `/cancelar` descarta el formulario.
- `/pagina N` muestra otra página de la última respuesta sin repetir la operación.

Compilar desde `untitled` con `mvn package` y ejecutar `java -jar target/donatrack-telegram.jar`.

Configurar `TELEGRAM_BOT_TOKEN` y, si corresponde, `TELEGRAM_BOT_USERNAME`. Se conserva el archivo de propiedades existente; `TELEGRAM_CONFIG` permite indicar uno externo. Las variables de entorno tienen prioridad. No guardar el token en Git.

`GATEWAY_URL` configura el acceso a los servicios desde una sola URL. Se mantienen las URLs individuales como overrides. `TELEGRAM_ADMIN_CHAT_IDS` es opcional y limita los chats autorizados para seleccionar Administrador; `/mi_chat` muestra el identificador.

Mantener una sola instancia por token. La selección de rol y los comandos con parámetros anteriores siguen disponibles.
