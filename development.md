# Install 
https://www.atlassian.com/software/confluence/download-archives

- Apache Commons FileUpload Bundle
http://central.maven.org/maven2/commons-fileupload/commons-fileupload/1.3/commons-fileupload-1.3.jar
- Atlassian PDK Install Plugin
http://maven-us.nuxeo.org/nexus/content/repositories/public/com/atlassian/pdkinstall/pdkinstall-plugin/0.6/pdkinstall-plugin-0.6.jar

## License 
https://my.atlassian.com/products/index

# Run
```shell script
set CATALINA_HOME=C:\Users\Public\dev\atlas\Confluence
set JPDA_ADDRESS=4444
set JPDA_TRANSPORT=dt_socket
%CATALINA_HOME%\bin\catalina.bat jpda start
```

http://127.0.0.1:8090/
```shell script
atlas-install-plugin -p 8090 --context-path / --plugin-key com.baloise.confluence.digital-signature
```

-------------------

Pure Maven setup (not for the faint of heart, startup is slow on my box)
you will be able to remote debug on port 5005

```shell script
mvn confluence:debug -Dproduct.version=7.4.0 
```

To redeploy use 
```shell script
mvn package confluence:install -Dproduct.version=7.4.0 
```

As smtp server use `https://mailtrap.io/`
