# NextGen Optimized

Aplicativo Windows em Java 17 e JavaFX para monitoramento de hardware,
inspecao de processos, ajustes de desempenho e preferencias de privacidade.

## Executar

Abra `NextGenOptimized.exe` na raiz. O aplicativo solicita permissao de
administrador para consultar e alterar recursos do Windows. Requer Java 17
ou superior instalado, ou um runtime compativel na pasta `runtime`.

Em **Privacidade Windows**, consulte e desative o Recall, a coleta de
digitacao e preferencias de publicidade. As alteracoes sao iniciadas pelo
usuario. O Recall depende da disponibilidade no Windows e pode exigir
reinicializacao. Backups de registro ficam em `%USERPROFILE%\.nextgen\backups`.

A integracao NVIDIA requer o NVIDIA Profile Inspector, obtido separadamente
e colocado em `nvidia/nvidiaProfileInspector.exe`. Esse programa de terceiros
nao esta incluido no repositorio.

## Compilar

Com JDK 17+ e Maven instalados:

```powershell
mvn -DskipTests package
```

O executavel gerado fica em `target/NextGenOptimized.exe`.

## Verificar

Os testes independentes de privacidade usam substitutos em memoria e nao
alteram configuracoes do Windows:

```powershell
javac -encoding UTF-8 -cp target/nextgen-optimizer-1.0.0-shaded.jar -d target/verification src/test/java/PrivacyChecks.java
java -cp 'target/verification;target/nextgen-optimizer-1.0.0-shaded.jar' PrivacyChecks
```

`src/test/java/UiSmoke.java` abre o aplicativo e captura as telas em `target`.
Nao aplica os ajustes de privacidade durante a verificacao visual.
