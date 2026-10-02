# Bot de DonaTrack

Compilar y probar con Java 21: `cd untitled` y `mvn package`. El JAR ejecutable queda en `target/donatrack-telegram.jar`.

Configurar `TELEGRAM_BOT_USERNAME`, `TELEGRAM_BOT_TOKEN` y `DONACIONES_API_URL`, `DONADORES_API_URL`, `LOGISTICA_API_URL`, `INCENTIVOS_API_URL`. Las variables de entorno tienen prioridad sobre application.properties. La configuración local no se publicó.

Usar `/admin` y `/ayuda donaciones`, `/ayuda donadores`, `/ayuda logistica` o `/ayuda incentivos`. Cada herramienta de `untitled/src/main/resources/tools.json` tiene un comando del mismo nombre. Las altas y modificaciones reciben un objeto JSON; las operaciones sobre un recurso reciben primero su ID. Las reglas de negocio permanecen en las APIs.

Se conservan `/depositos`, `/stock ID`, `/insignias`, `/procesar_donador ID`, las consultas anteriores del donador y los comandos antiguos con separadores `|`. Los mensajes largos se dividen para respetar el límite de Telegram. Los logs registran el comando y no el mensaje completo.

Las pruebas usan HTTP local y no requieren token de Telegram. El CI compila y prueba automáticamente. Para cerrar la demo hay que ejecutar el bot con el token real y comprobar los cuatro servicios. `/admin` selecciona un rol de demostración; no autentica administradores.
