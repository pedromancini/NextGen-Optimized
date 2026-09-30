package com.nextgen.optimizer.tweaks;

import com.nextgen.optimizer.services.RegistryService;
import com.nextgen.optimizer.services.SteamLocator;
import com.nextgen.optimizer.tweaks.Tweak.Category;
import com.nextgen.optimizer.tweaks.Tweak.Profile;
import com.nextgen.optimizer.tweaks.Tweak.Risk;
import com.sun.jna.platform.win32.WinReg;

import java.util.ArrayList;
import java.util.List;

import static com.nextgen.optimizer.tweaks.RegistryAction.dword;
import static com.nextgen.optimizer.tweaks.RegistryAction.string;

/**
 * Every optimization NextGen X can apply. Rules for inclusion:
 * <ul>
 *   <li>fully reversible (registry, service start type or power index);</li>
 *   <li>no deletion of system components, drivers or boot configuration;</li>
 *   <li>descriptions state the real effect and trade-off, not marketing.</li>
 * </ul>
 * Order matters: the power plan is switched before per-plan power settings.
 */
public final class TweakCatalog {

    private static final WinReg.HKEY HKCU = WinReg.HKEY_CURRENT_USER;
    private static final WinReg.HKEY HKLM = WinReg.HKEY_LOCAL_MACHINE;

    private static final String GAME_CONFIG = "System\\GameConfigStore";
    private static final String GAME_BAR = "Software\\Microsoft\\GameBar";
    private static final String MULTIMEDIA = "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Multimedia\\SystemProfile";
    private static final String MMCSS_GAMES = MULTIMEDIA + "\\Tasks\\Games";
    private static final String IFEO = "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Image File Execution Options";
    private static final String GPU_PREFS = "Software\\Microsoft\\DirectX\\UserGpuPreferences";
    private static final String COMPAT_LAYERS = "Software\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Layers";
    private static final String CDM = "Software\\Microsoft\\Windows\\CurrentVersion\\ContentDeliveryManager";
    private static final String TCPIP_INTERFACES = "SYSTEM\\CurrentControlSet\\Services\\Tcpip\\Parameters\\Interfaces";

    // Power setting GUIDs (sub group / setting)
    private static final String SUB_PROCESSOR = "54533251-82be-4824-96c1-47b60b740d00";
    private static final String PROC_THROTTLE_MIN = "893dee8e-2bef-41e0-89c6-b55d0929964c";
    private static final String CP_MIN_CORES = "0cc5b647-c1df-4637-891a-dec35c318583";
    private static final String SUB_PCIE = "501a4d13-42af-4429-9fd1-a8218c268e20";
    private static final String ASPM = "ee12f906-d277-404b-b6da-e5fa1a576df5";
    private static final String SUB_USB = "2a737441-1930-4402-8d77-b2bebba308a3";
    private static final String USB_SUSPEND = "48e6b7a6-50f5-4782-a5d4-53bb8f07e226";
    private static final String SUB_DISK = "0012ee47-9041-4b5d-9b77-535fba8b1442";
    private static final String DISK_IDLE = "6738e2c4-e8a5-4a42-b16a-e040e769756e";

    private TweakCatalog() {}

    public static List<Tweak> build(RegistryService registry, SteamLocator steam) {
        List<Tweak> t = new ArrayList<>();

        // ═══ Jogos & FPS ═════════════════════════════════════════════
        t.add(Tweak.builder("game-mode", Category.GAMES)
                .title("Modo de Jogo do Windows")
                .description("Faz o Windows priorizar o jogo em execução e adiar atualizações e notificações durante a partida.")
                .impact("FPS", "Estabilidade").profile(Profile.SAFE)
                .action(dword(HKCU, GAME_BAR, "AutoGameModeEnabled", 1))
                .action(dword(HKCU, GAME_BAR, "AllowAutoGameMode", 1))
                .build());
        t.add(Tweak.builder("game-dvr-off", Category.GAMES)
                .title("Desativar gravação em segundo plano (Game DVR)")
                .description("Impede a Xbox Game Bar de gravar a tela continuamente, o que consome GPU e disco durante o jogo.")
                .warning("Clipes da Xbox Game Bar deixam de funcionar. Use o gravador da NVIDIA/AMD ou OBS.")
                .impact("FPS", "1% Low").profile(Profile.SAFE)
                .action(dword(HKCU, GAME_CONFIG, "GameDVR_Enabled", 0))
                .action(dword(HKCU, "Software\\Microsoft\\Windows\\CurrentVersion\\GameDVR", "AppCaptureEnabled", 0))
                .action(dword(HKLM, "SOFTWARE\\Policies\\Microsoft\\Windows\\GameDVR", "AllowGameDVR", 0))
                .build());
        t.add(Tweak.builder("gamebar-tips-off", Category.GAMES)
                .title("Silenciar a Xbox Game Bar")
                .description("Remove o painel de boas-vindas e impede que o botão Xbox do controle abra a Game Bar no meio da partida.")
                .impact("Foco").profile(Profile.GAMER)
                .action(dword(HKCU, GAME_BAR, "ShowStartupPanel", 0))
                .action(dword(HKCU, GAME_BAR, "UseNexusForGameBarEnabled", 0))
                .build());
        t.add(Tweak.builder("mmcss-games", Category.GAMES)
                .title("Prioridade multimídia para jogos (MMCSS)")
                .description("Dá à categoria \"Games\" do agendador multimídia prioridade alta de CPU, GPU e disco.")
                .impact("FPS", "1% Low").profile(Profile.GAMER)
                .action(dword(HKLM, MMCSS_GAMES, "GPU Priority", 8))
                .action(dword(HKLM, MMCSS_GAMES, "Priority", 6))
                .action(string(HKLM, MMCSS_GAMES, "Scheduling Category", "High"))
                .action(string(HKLM, MMCSS_GAMES, "SFIO Priority", "High"))
                .build());
        t.add(Tweak.builder("system-responsiveness", Category.GAMES)
                .title("Reservar menos CPU para tarefas de fundo")
                .description("Reduz de 20% para 10% a fatia de CPU reservada para processos de baixa prioridade enquanto há mídia/jogo ativo.")
                .impact("FPS").profile(Profile.GAMER)
                .action(dword(HKLM, MULTIMEDIA, "SystemResponsiveness", 10))
                .build());
        t.add(Tweak.builder("windowed-opt", Category.GAMES)
                .title("Otimizações para jogos em janela")
                .description("Usa o modelo de apresentação \"flip\" em jogos em janela sem borda (Windows 11), reduzindo a latência para níveis de tela cheia.")
                .impact("Latência").profile(Profile.GAMER)
                .action(string(HKCU, GPU_PREFS, "DirectXUserGlobalSettings", "SwapEffectUpgradeEnable=1;"))
                .build());
        t.add(Tweak.builder("priority-separation", Category.GAMES)
                .title("Priorizar o programa em primeiro plano")
                .description("Configura o agendador (Win32PrioritySeparation = 0x26) para dar mais tempo de CPU ao jogo em foco.")
                .impact("1% Low", "Latência").risk(Risk.MODERATE).profile(Profile.COMPETITIVE)
                .action(dword(HKLM, "SYSTEM\\CurrentControlSet\\Control\\PriorityControl", "Win32PrioritySeparation", 0x26))
                .build());
        t.add(Tweak.builder("fso-global-off", Category.GAMES)
                .title("Tela cheia exclusiva de verdade (global)")
                .description("Desativa as \"otimizações de tela cheia\" do Windows para jogos DirectX, garantindo o modo exclusivo quando o jogo pedir.")
                .warning("Alt+Tab pode ficar mais lento em jogos em tela cheia.")
                .impact("Latência").risk(Risk.MODERATE).profile(Profile.COMPETITIVE)
                .action(dword(HKCU, GAME_CONFIG, "GameDVR_FSEBehaviorMode", 2))
                .action(dword(HKCU, GAME_CONFIG, "GameDVR_HonorUserFSEBehaviorMode", 1))
                .action(dword(HKCU, GAME_CONFIG, "GameDVR_DXGIHonorFSEWindowsCompatible", 1))
                .action(dword(HKCU, GAME_CONFIG, "GameDVR_EFSEFeatureFlags", 0))
                .build());
        t.add(Tweak.builder("hags", Category.GAMES)
                .title("Agendamento de GPU acelerado por hardware (HAGS)")
                .description("Deixa a própria GPU gerenciar sua fila de trabalho. Necessário para Frame Generation (DLSS 3/FSR 3).")
                .warning("Em algumas GPUs piora o 1% Low no CS2. Teste com e sem.")
                .impact("Latência").risk(Risk.MODERATE).restart()
                .action(dword(HKLM, "SYSTEM\\CurrentControlSet\\Control\\GraphicsDrivers", "HwSchMode", 2))
                .build());
        t.add(Tweak.builder("mpo-off", Category.GAMES)
                .title("Desativar Multiplane Overlay (MPO)")
                .description("Corrige cintilação, telas pretas e travadas ao alternar janelas causadas pelo MPO em alguns drivers.")
                .warning("Use só se tiver esses sintomas. Em versões recentes do Windows 11 pode não ter efeito.")
                .impact("Estabilidade").risk(Risk.ADVANCED).restart()
                .action(dword(HKLM, "SOFTWARE\\Microsoft\\Windows\\Dwm", "OverlayTestMode", 5))
                .build());

        // ═══ Counter-Strike 2 ════════════════════════════════════════
        t.add(Tweak.builder("cs2-priority", Category.CS2)
                .title("CS2 sempre com prioridade alta")
                .description("Registra no Windows que cs2.exe deve iniciar com prioridade de CPU alta, sem precisar do Gerenciador de Tarefas.")
                .impact("1% Low", "FPS").profile(Profile.GAMER)
                .action(dword(HKLM, IFEO + "\\cs2.exe\\PerfOptions", "CpuPriorityClass", 3))
                .build());
        t.add(Tweak.builder("cs2-gpu-pref", Category.CS2)
                .title("CS2 na GPU de alto desempenho")
                .description("Força o CS2 a usar a placa de vídeo dedicada. Essencial em notebooks com GPU integrada + dedicada.")
                .impact("FPS").profile(Profile.GAMER)
                .action(new RegistryAction(HKCU, GPU_PREFS, steam::cs2ExecutableString, "GpuPreference=2;", false))
                .build());
        t.add(Tweak.builder("cs2-fso-off", Category.CS2)
                .title("CS2 sem otimizações de tela cheia")
                .description("Marca o cs2.exe para ignorar a camada de composição do Windows em tela cheia, reduzindo o input lag.")
                .impact("Latência").risk(Risk.MODERATE).profile(Profile.COMPETITIVE)
                .action(new RegistryAction(HKCU, COMPAT_LAYERS, steam::cs2ExecutableString, "~ DISABLEDXMAXIMIZEDWINDOWEDMODE", false))
                .build());

        // ═══ Latência & Input ════════════════════════════════════════
        t.add(Tweak.builder("mouse-accel-off", Category.LATENCY)
                .title("Desativar aceleração do mouse")
                .description("Desliga o \"Aumentar precisão do ponteiro\": o mesmo movimento da mão sempre gera o mesmo movimento na mira.")
                .impact("Mira").profile(Profile.SAFE).restart()
                .action(string(HKCU, "Control Panel\\Mouse", "MouseSpeed", "0"))
                .action(string(HKCU, "Control Panel\\Mouse", "MouseThreshold1", "0"))
                .action(string(HKCU, "Control Panel\\Mouse", "MouseThreshold2", "0"))
                .build());
        t.add(Tweak.builder("sticky-keys-off", Category.LATENCY)
                .title("Desativar atalhos de Teclas de Aderência")
                .description("Evita que apertar Shift 5 vezes (comum ao andar em silêncio) abra uma janela e minimize o jogo.")
                .impact("Foco").profile(Profile.SAFE)
                .action(string(HKCU, "Control Panel\\Accessibility\\StickyKeys", "Flags", "506"))
                .action(string(HKCU, "Control Panel\\Accessibility\\ToggleKeys", "Flags", "58"))
                .action(string(HKCU, "Control Panel\\Accessibility\\Keyboard Response", "Flags", "122"))
                .build());
        t.add(Tweak.builder("keyboard-fast", Category.LATENCY)
                .title("Repetição de teclado mais rápida")
                .description("Reduz o atraso até a tecla começar a repetir e aumenta a velocidade de repetição ao máximo.")
                .impact("Input")
                .action(string(HKCU, "Control Panel\\Keyboard", "KeyboardDelay", "0"))
                .action(string(HKCU, "Control Panel\\Keyboard", "KeyboardSpeed", "31"))
                .build());
        t.add(Tweak.builder("usb-suspend-off", Category.LATENCY)
                .title("Nunca suspender portas USB")
                .description("Impede que o Windows economize energia desligando portas USB — evita atrasos e desconexões de mouse, teclado e headset.")
                .impact("Input", "Estabilidade").profile(Profile.GAMER)
                .action(new PowerSettingAction(SUB_USB, USB_SUSPEND, 0, "Suspensão seletiva de USB"))
                .build());

        // ═══ Rede ════════════════════════════════════════════════════
        t.add(Tweak.builder("network-throttling-off", Category.NETWORK)
                .title("Remover limitação de rede multimídia")
                .description("Desativa o limite de processamento de pacotes que o Windows aplica enquanto há áudio/vídeo tocando.")
                .impact("Ping", "Estabilidade").profile(Profile.GAMER)
                .action(dword(HKLM, MULTIMEDIA, "NetworkThrottlingIndex", 0xFFFFFFFF))
                .build());
        t.add(Tweak.builder("delivery-opt-p2p-off", Category.NETWORK)
                .title("Não compartilhar atualizações com outros PCs")
                .description("Impede o Windows de enviar pedaços de atualizações para outros computadores pela sua internet (Otimização de Entrega P2P).")
                .impact("Ping", "Banda").profile(Profile.SAFE)
                .action(dword(HKLM, "SOFTWARE\\Policies\\Microsoft\\Windows\\DeliveryOptimization", "DODownloadMode", 0))
                .build());
        t.add(Tweak.builder("nagle-off", Category.NETWORK)
                .title("Desativar algoritmo de Nagle")
                .description("Envia pacotes TCP imediatamente em vez de agrupá-los. Ajuda jogos que usam TCP (alguns MMOs).")
                .warning("O CS2 usa UDP e não é afetado por este ajuste.")
                .impact("Ping").risk(Risk.MODERATE)
                .actions(nagleActions(registry))
                .build());
        t.add(Tweak.builder("background-apps-off", Category.NETWORK)
                .title("Bloquear apps da Store em segundo plano")
                .description("Impede que aplicativos da Microsoft Store rodem escondidos consumindo rede, CPU e RAM.")
                .warning("Apps da Store (ex.: WhatsApp da Store) param de receber notificações com a janela fechada.")
                .impact("RAM", "Banda").risk(Risk.MODERATE)
                .action(dword(HKLM, "SOFTWARE\\Policies\\Microsoft\\Windows\\AppPrivacy", "LetAppsRunInBackground", 2))
                .build());

        // ═══ Memória & Disco ═════════════════════════════════════════
        t.add(Tweak.builder("edge-background-off", Category.MEMORY)
                .title("Impedir o Edge de ficar aberto escondido")
                .description("Desliga o \"Startup Boost\" e a execução em segundo plano do Microsoft Edge, que mantêm processos ocupando RAM.")
                .impact("RAM").profile(Profile.SAFE)
                .action(dword(HKLM, "SOFTWARE\\Policies\\Microsoft\\Edge", "StartupBoostEnabled", 0))
                .action(dword(HKLM, "SOFTWARE\\Policies\\Microsoft\\Edge", "BackgroundModeEnabled", 0))
                .build());
        t.add(Tweak.builder("chrome-background-off", Category.MEMORY)
                .title("Impedir o Chrome de ficar aberto escondido")
                .description("Fecha o Google Chrome de verdade ao fechar a última janela, liberando a RAM de extensões e apps em segundo plano.")
                .impact("RAM").profile(Profile.SAFE)
                .action(dword(HKLM, "SOFTWARE\\Policies\\Google\\Chrome", "BackgroundModeEnabled", 0))
                .build());
        t.add(Tweak.builder("startup-delay-off", Category.MEMORY)
                .title("Remover atraso de inicialização de apps")
                .description("Tira a espera artificial que o Windows aplica antes de abrir os programas da inicialização.")
                .impact("Boot").profile(Profile.SAFE)
                .action(dword(HKCU, "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Serialize", "StartupDelayInMSec", 0))
                .build());
        t.add(Tweak.builder("ntfs-last-access-off", Category.MEMORY)
                .title("Não registrar último acesso de arquivos")
                .description("Evita uma escrita no disco a cada arquivo lido (carregamento de mapas e texturas).")
                .impact("Disco").profile(Profile.GAMER)
                .action(dword(HKLM, "SYSTEM\\CurrentControlSet\\Control\\FileSystem", "NtfsDisableLastAccessUpdate", 0x80000001))
                .build());
        t.add(Tweak.builder("fast-startup-off", Category.MEMORY)
                .title("Desativar Inicialização Rápida")
                .description("Faz \"Desligar\" ser um desligamento completo: drivers e memória começam limpos a cada boot.")
                .warning("O boot fica alguns segundos mais lento.")
                .impact("Estabilidade").profile(Profile.GAMER)
                .action(dword(HKLM, "SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Power", "HiberbootEnabled", 0))
                .build());
        t.add(Tweak.builder("sysmain-off", Category.MEMORY)
                .title("Desativar SysMain (SuperFetch)")
                .description("Para o pré-carregamento de apps na RAM. Útil com 8 GB de RAM ou menos, ou quando o disco fica em 100%.")
                .warning("Recomendado apenas com SSD. Em HD mecânico os apps podem abrir mais devagar.")
                .impact("RAM", "Disco").risk(Risk.MODERATE)
                .action(new ServiceAction("SysMain", ServiceAction.DISABLED))
                .build());
        t.add(Tweak.builder("hibernate-off", Category.MEMORY)
                .title("Desativar hibernação")
                .description("Apaga o hiberfil.sys e libera no disco um espaço equivalente a ~40% da sua RAM.")
                .warning("Desativa também a Inicialização Rápida e a opção Hibernar.")
                .impact("Disco").risk(Risk.MODERATE)
                .action(new HibernateAction())
                .build());
        t.add(Tweak.builder("paging-executive", Category.MEMORY)
                .title("Manter o kernel na RAM")
                .description("Impede que drivers e o núcleo do Windows sejam movidos para o arquivo de paginação.")
                .warning("Só faz diferença em PCs com pouca RAM sob uso intenso.")
                .impact("Latência").risk(Risk.MODERATE).restart()
                .action(dword(HKLM, "SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Memory Management", "DisablePagingExecutive", 1))
                .build());

        // ═══ Energia & CPU ═══════════════════════════════════════════
        t.add(Tweak.builder("power-plan", Category.POWER)
                .title("Plano de energia Desempenho Máximo")
                .description("Ativa o plano Ultimate Performance (criado a partir do modelo oculto do Windows) ou Alto Desempenho.")
                .warning("Em notebooks, aumenta consumo e temperatura. Em PCs com Modern Standby o plano pode não existir.")
                .impact("FPS", "1% Low").risk(Risk.MODERATE).profile(Profile.GAMER)
                .action(new PowerPlanAction())
                .build());
        t.add(Tweak.builder("pcie-aspm-off", Category.POWER)
                .title("PCI Express sem economia de energia")
                .description("Desliga o ASPM do link PCIe da GPU e do SSD NVMe, eliminando a latência de \"acordar\" o dispositivo.")
                .impact("Latência").profile(Profile.GAMER)
                .action(new PowerSettingAction(SUB_PCIE, ASPM, 0, "Gerenciamento de energia do link PCIe"))
                .build());
        t.add(Tweak.builder("disk-idle-off", Category.POWER)
                .title("Discos nunca entram em repouso")
                .description("Evita que HDs desliguem e causem travadas de alguns segundos ao carregar um mapa.")
                .impact("Estabilidade").profile(Profile.GAMER)
                .action(new PowerSettingAction(SUB_DISK, DISK_IDLE, 0, "Desligar disco após"))
                .build());
        t.add(Tweak.builder("cpu-min-100", Category.POWER)
                .title("CPU sempre em clock máximo")
                .description("Define o estado mínimo do processador em 100% na tomada: sem atraso para subir a frequência.")
                .warning("Aumenta temperatura e consumo em repouso. Não afeta o uso na bateria.")
                .impact("1% Low", "Latência").risk(Risk.MODERATE).profile(Profile.COMPETITIVE)
                .action(new PowerSettingAction(SUB_PROCESSOR, PROC_THROTTLE_MIN, 100, "Estado mínimo do processador"))
                .build());
        t.add(Tweak.builder("core-parking-off", Category.POWER)
                .title("Desativar estacionamento de núcleos")
                .description("Mantém todos os núcleos da CPU acordados para absorver picos de carga sem atraso.")
                .warning("Aumenta consumo em repouso.")
                .impact("1% Low").risk(Risk.MODERATE).profile(Profile.COMPETITIVE)
                .action(new PowerSettingAction(SUB_PROCESSOR, CP_MIN_CORES, 100, "Mínimo de núcleos ativos"))
                .build());
        t.add(Tweak.builder("power-throttling-off", Category.POWER)
                .title("Desativar Power Throttling")
                .description("Impede o Windows de reduzir a CPU de programas que ele considera em segundo plano (Discord, OBS, overlay).")
                .warning("Em notebooks reduz a duração da bateria.")
                .impact("Estabilidade").risk(Risk.MODERATE).profile(Profile.COMPETITIVE)
                .action(dword(HKLM, "SYSTEM\\CurrentControlSet\\Control\\Power\\PowerThrottling", "PowerThrottlingOff", 1))
                .build());

        // ═══ Serviços ════════════════════════════════════════════════
        t.add(service("svc-diagtrack", "DiagTrack", "Telemetria (Experiências do Usuário Conectado)",
                "Para o envio contínuo de dados de diagnóstico, que usa CPU, disco e rede em segundo plano.",
                null, Risk.SAFE, Profile.SAFE));
        t.add(service("svc-dmwappush", "dmwappushservice", "Roteamento de mensagens WAP",
                "Serviço auxiliar da telemetria. Não é usado em PCs domésticos.",
                "Necessário apenas em PCs gerenciados por empresa (Intune/MDM).", Risk.SAFE, Profile.GAMER));
        t.add(service("svc-retaildemo", "RetailDemo", "Modo de demonstração de loja",
                "Serviço usado apenas em PCs de vitrine.", null, Risk.SAFE, Profile.SAFE));
        t.add(service("svc-mapsbroker", "MapsBroker", "Gerenciador de mapas baixados",
                "Atualiza mapas offline do app Mapas em segundo plano.", null, Risk.SAFE, Profile.GAMER));
        t.add(service("svc-wersvc", "WerSvc", "Relatório de Erros do Windows",
                "Evita picos de CPU e disco ao gerar e enviar relatórios de falha.",
                null, Risk.SAFE, Profile.GAMER));
        t.add(service("svc-fax", "Fax", "Fax", "Serviço de fax do Windows.", null, Risk.SAFE, Profile.GAMER));
        t.add(service("svc-remoteregistry", "RemoteRegistry", "Registro Remoto",
                "Impede que o registro seja editado pela rede. Melhora a segurança.", null, Risk.SAFE, Profile.SAFE));
        t.add(service("svc-wsearch", "WSearch", "Indexação de pesquisa (Windows Search)",
                "Para a indexação contínua de arquivos, que consome disco e CPU.",
                "A pesquisa do Menu Iniciar e do Explorador fica mais lenta e o Outlook pode não pesquisar e-mails.",
                Risk.ADVANCED, null));
        t.add(service("svc-spooler", "Spooler", "Spooler de impressão",
                "Desativa o serviço de impressão (e fecha uma porta comum de vulnerabilidades).",
                "Você não conseguirá imprimir nem usar \"Imprimir em PDF\".", Risk.ADVANCED, null));

        // ═══ Windows & Interface ═════════════════════════════════════
        t.add(Tweak.builder("transparency-off", Category.SYSTEM)
                .title("Desativar transparência")
                .description("Remove o efeito de vidro do Menu Iniciar e da barra de tarefas, poupando a GPU.")
                .impact("GPU").profile(Profile.GAMER)
                .action(dword(HKCU, "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize", "EnableTransparency", 0))
                .build());
        t.add(Tweak.builder("animations-off", Category.SYSTEM)
                .title("Desativar animações de janelas")
                .description("Janelas e barra de tarefas abrem e minimizam instantaneamente.")
                .impact("Fluidez").profile(Profile.GAMER).restart()
                .action(string(HKCU, "Control Panel\\Desktop\\WindowMetrics", "MinAnimate", "0"))
                .action(dword(HKCU, "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Advanced", "TaskbarAnimations", 0))
                .build());
        t.add(Tweak.builder("menu-delay", Category.SYSTEM)
                .title("Menus sem atraso")
                .description("Remove a espera de 400 ms antes de abrir submenus.")
                .impact("Fluidez").profile(Profile.SAFE).restart()
                .action(string(HKCU, "Control Panel\\Desktop", "MenuShowDelay", "0"))
                .build());
        t.add(Tweak.builder("widgets-off", Category.SYSTEM)
                .title("Desativar Widgets (Notícias e Interesses)")
                .description("Remove o painel de widgets e seus processos WebView que ficam carregados na RAM.")
                .impact("RAM").profile(Profile.GAMER)
                .action(dword(HKLM, "SOFTWARE\\Policies\\Microsoft\\Dsh", "AllowNewsAndInterests", 0))
                .build());
        t.add(Tweak.builder("suggestions-off", Category.SYSTEM)
                .title("Sem sugestões, dicas e apps instalados sozinhos")
                .description("Desliga anúncios do Menu Iniciar, dicas do Windows e a instalação automática de apps patrocinados.")
                .impact("Limpeza").profile(Profile.SAFE)
                .action(dword(HKCU, CDM, "SystemPaneSuggestionsEnabled", 0))
                .action(dword(HKCU, CDM, "SubscribedContent-338388Enabled", 0))
                .action(dword(HKCU, CDM, "SubscribedContent-338389Enabled", 0))
                .action(dword(HKCU, CDM, "SilentInstalledAppsEnabled", 0))
                .action(dword(HKCU, CDM, "SoftLandingEnabled", 0))
                .build());
        t.add(Tweak.builder("bing-search-off", Category.SYSTEM)
                .title("Pesquisa do Menu Iniciar só local")
                .description("Remove os resultados do Bing da busca do Menu Iniciar: mais rápida e sem enviar o que você digita.")
                .impact("Privacidade").profile(Profile.SAFE).restart()
                .action(dword(HKCU, "Software\\Policies\\Microsoft\\Windows\\Explorer", "DisableSearchBoxSuggestions", 1))
                .build());
        t.add(Tweak.builder("telemetry-min", Category.SYSTEM)
                .title("Telemetria no nível mínimo")
                .description("Reduz os dados de diagnóstico ao mínimo permitido pela sua edição do Windows.")
                .impact("Privacidade").profile(Profile.SAFE)
                .action(dword(HKLM, "SOFTWARE\\Policies\\Microsoft\\Windows\\DataCollection", "AllowTelemetry", 0))
                .build());

        return t;
    }

    private static Tweak service(String id, String service, String title, String description,
                                 String warning, Risk risk, Profile profile) {
        return Tweak.builder(id, Category.SERVICES)
                .title("Desativar: " + title)
                .description(description)
                .warning(warning)
                .impact("RAM", "CPU").risk(risk).profile(profile)
                .action(new ServiceAction(service, ServiceAction.DISABLED))
                .build();
    }

    /** One TcpAckFrequency/TCPNoDelay pair per network interface that has an IP address. */
    private static List<TweakAction> nagleActions(RegistryService registry) {
        List<TweakAction> actions = new ArrayList<>();
        for (String guid : registry.getSubKeys(HKLM, TCPIP_INTERFACES)) {
            String path = TCPIP_INTERFACES + "\\" + guid;
            if (isUsableIp(registry.getRawValue(HKLM, path, "DhcpIPAddress"))
                    || isUsableIp(registry.getRawValue(HKLM, path, "IPAddress"))) {
                actions.add(dword(HKLM, path, "TcpAckFrequency", 1));
                actions.add(dword(HKLM, path, "TCPNoDelay", 1));
            }
        }
        if (actions.isEmpty()) {
            // Keeps the tweak valid; reported as "not applicable" because the key is required.
            actions.add(new RegistryAction(HKLM, TCPIP_INTERFACES + "\\{none}", () -> "TCPNoDelay", 1, true));
        }
        return actions;
    }

    /** Accepts REG_SZ (DHCP) and REG_MULTI_SZ (static) addresses. */
    private static boolean isUsableIp(Object value) {
        String[] ips = value instanceof String[] arr ? arr : value == null ? new String[0] : new String[]{value.toString()};
        for (String ip : ips) {
            if (ip != null && !ip.isBlank() && !ip.startsWith("0.0.0.0")) return true;
        }
        return false;
    }
}
