# NextGen X — Performance Suite

Otimizador para Windows 10/11 focado em jogos (especialmente Counter-Strike 2),
feito em Java 17 + JavaFX. Todo ajuste é **reversível**: o valor original é
salvo antes de qualquer alteração e pode ser restaurado individualmente ou
todos de uma vez.

## Executar

Abra `NextGenX.exe` na raiz. O aplicativo pede permissão de administrador
(necessária para ajustes do sistema e limpeza de RAM). Requer Java 17+ instalado
ou um runtime compatível na pasta `runtime`.

## O que faz

| Área | Recursos |
| --- | --- |
| **Otimização Full** | Perfis *Essencial*, *Gamer* e *Competitivo CS2*; mostra exatamente o que muda, cria ponto de restauração do Windows, aplica com progresso ao vivo e oferece "Reverter tudo". |
| **Central de Ajustes** | 51 ajustes em 8 categorias (Jogos, CS2, Latência, Rede, Memória & Disco, Energia & CPU, Serviços, Windows) com busca, filtros, nível de risco e detalhes técnicos. |
| **Counter-Strike 2** | Detecta o jogo em qualquer biblioteca da Steam; prioridade alta via registro, GPU dedicada, tela cheia exclusiva; `autoexec.cfg` com bloco gerenciado (preserva suas binds, com backup); limitador RTSS só para `cs2.exe`. |
| **Game Booster** | Ao abrir o jogo: prioridade alta, plano Desempenho Máximo e limpeza leve de RAM. Tudo é desfeito ao fechar — inclusive se o app fechar inesperadamente. |
| **Memória RAM** | Limpeza real pela API de listas de memória do Windows (a mesma do RAMMap): leve, profunda e working sets. RAM Guard automático por limite de memória livre. |
| **Limpeza** | Analisa antes de apagar: temporários, relatórios de erro, logs antigos, Otimização de Entrega, navegadores, Windows Update, Lixeira e caches de shader. |
| **Inicialização** | Liga/desliga programas como o Gerenciador de Tarefas (sem apagar entradas). |

### Garantias de segurança

- Nenhum ajuste remove componentes, drivers ou mexe na configuração de boot.
- Valores originais ficam em `%USERPROFILE%\.nextgen\tweaks\state.json`; se o
  backup não puder ser salvo, nada é alterado.
- Falha no meio de um ajuste desfaz as partes já aplicadas.
- Ajustes *Avançados* nunca entram nos perfis automáticos.
- A Otimização Full cria um ponto de restauração do Windows antes de começar.

A integração NVIDIA requer o NVIDIA Profile Inspector, obtido separadamente e
colocado em `nvidia/nvidiaProfileInspector.exe`.

## Compilar

Com JDK 17+ e Maven:

```powershell
mvn -DskipTests package
```

Gera `target/NextGenX.exe` e `target/nextgen-optimizer-2.0.0-shaded.jar`.
Para regenerar o ícone: `java -cp target/classes com.nextgen.optimizer.tools.LogoGenerator`.

## Verificar

Os testes usam registro e planos de energia em memória — **não alteram o Windows**:

```powershell
$cp = "target/nextgen-optimizer-2.0.0-shaded.jar"
javac -encoding UTF-8 -cp $cp -d target/verification src/test/java/TweakChecks.java src/test/java/PrivacyChecks.java src/test/java/UiSmoke.java
java -cp "target/verification;$cp" TweakChecks
java -cp "target/verification;$cp" PrivacyChecks
java -cp "target/verification;$cp" UiSmoke   # abre o app e salva capturas em target/ui-*.png
```
