# Unidad 01 – Reto

## Reto

Ejecuta la aplicación en 8081 mediante configuración externa, sin editar la clase principal. Conserva la salida de versiones, el resultado de las pruebas y una explicación de por qué cambiar el puerto no exige recompilar la lógica Java. Antes de continuar a U02 vuelve a la configuración base 8080. Guarda tu reto en una rama aparte.

## Solución

En `U01/inicio/src/main/resources/application.properties` se modifica:

`server.port=8080` a `server.port=8081` despues con `./mvnw --version` verificamos que la version se mantenga igual:

```
Apache Maven 3.9.11 (3e54c93a704957b63ee3494413a2b544fd3d825b)
Maven home: /home/adrixn/.m2/wrapper/dists/apache-maven-3.9.11/a2d47e15
Java version: 25.0.4.1, vendor: Arch Linux, runtime: /usr/lib/jvm/java-25-openjdk
Default locale: en_US, platform encoding: UTF-8
OS name: "linux", version: "7.2.6-1-cachyos", arch: "amd64", family: "unix"
```

Con `./mvnw test` revisamos que no haya errores

```
[INFO] Results:
[INFO]
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
```

al final con `./mvnw spring-boot:run` verificamos que se utiliza el puerto 8081

```
2026-09-22T17:28:36.743-06:00 INFO 98865 --- [backend-academico] [ main] b.w.c.s.WebApplicationContextInitializer : Root WebApplicationContext: initialization completed in 296 ms
2026-09-22T17:28:36.892-06:00 INFO 98865 --- [backend-academico] [ main] o.s.boot.tomcat.TomcatWebServer : Tomcat started on port 8081 (http) with context path '/'
2026-09-22T17:28:36.895-06:00 INFO 98865 --- [backend-academico] [ main] m.e.b.BackendAcademicoApplication : Started BackendAcademicoApplication in 0.638 seconds (process running for 0.759)
```

Cambiar `server.port` en `application.properties` no exige recompilar la lógica Java porque Spring Boot separa el código (las clases .class ya compiladas) de su configuración externa. El puerto se lee en tiempo de ejecución desde ese archivo (o desde variables de entorno / argumentos de línea de comandos, con mayor prioridad), no está escrito como valor fijo dentro del bytecode. Por eso el artefacto compilado puede arrancar en 8080, 8081 o cualquier otro puerto solo cambiando la configuración, sin volver a compilar nada.
