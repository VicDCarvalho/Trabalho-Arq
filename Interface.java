import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

public class Interface extends JFrame {
    private Processador processador;
    private MontadorMacro montadorMacro;
    private MontadorMacro.ResultadoMontagem ultimoResultado;

    // Paleta de cores moderna
    private static final Color COR_FUNDO = new Color(24, 26, 33);
    private static final Color COR_PAINEL = new Color(33, 37, 46);
    private static final Color COR_TEXTO = new Color(235, 240, 248);
    private static final Color COR_DESTAQUE_AZUL = new Color(56, 139, 253);
    private static final Color COR_DESTAQUE_VERDE = new Color(46, 160, 67);
    private static final Color COR_DESTAQUE_LARANJA = new Color(210, 153, 34);
    private static final Color COR_DESTAQUE_ROXO = new Color(137, 87, 229);
    private static final Color COR_LINHA_ATIVA = new Color(45, 75, 120);

    private JTextArea txtAssembly;
    private JTextArea txtCompilado;
    private JTextArea txtMicroinstrucao;
    private JLabel lblInstrucaoAtual;
    private JLabel lblStatusCiclos;

    private JTable tabelaRegistradores;
    private DefaultTableModel modeloRegs;

    private JTable tabelaMP;
    private DefaultTableModel modeloMP;

    private JTable tabelaCache;
    private DefaultTableModel modeloCache;

    private PainelDatapath painelDatapath;

    private JSpinner spinnerPausa;
    private Timer timerExecucao;
    private JButton btnIniciar;

    private Map<String, String> bibliotecaProgramas;
    private JComboBox<String> comboProgramas;

    public Interface() {
        this.processador = new Processador();
        this.montadorMacro = new MontadorMacro();
        inicializarBibliotecaProgramas();
        configurarJanela();
        inicializarComponentes();
        atualizarTela();
    }

    private void inicializarBibliotecaProgramas() {
        bibliotecaProgramas = new LinkedHashMap<>();

        bibliotecaProgramas.put("1. Soma (10 + 25 = 35)",
            "// Macroprograma de Exemplo (Soma 10 + 25)\n" +
            "LOCO 10\n" +
            "STOD 20      // Guarda 10 no end. 20\n" +
            "LOCO 25\n" +
            "ADDD 20      // AC = 25 + 10 (35)\n" +
            "STOD 21      // Guarda resultado no end. 21\n" +
            "HALT         // Encerra execução\n");

        bibliotecaProgramas.put("2. Multiplicação (4 x 3 = 12)",
            "// Multiplicação por somas sucessivas (4 x 3 = 12)\n" +
            "LOCO 4\n" +
            "STOD 20      // Multiplicando = 4\n" +
            "LOCO 3\n" +
            "STOD 21      // Multiplicador = 3 (contador)\n" +
            "LOCO 0\n" +
            "STOD 22      // Acumulador do produto = 0\n" +
            "LOOP: LODD 21\n" +
            "JZER FIM\n" +
            "SUBD 16      // Decrementa contador (DATA 1 no end. 16)\n" +
            "STOD 21\n" +
            "LODD 22\n" +
            "ADDD 20\n" +
            "STOD 22\n" +
            "JUMP LOOP\n" +
            "FIM: LODD 22 // Carrega resultado (12) no AC\n" +
            "HALT\n" +
            "DATA 1\n");

        bibliotecaProgramas.put("3. Fibonacci (F5 = 5)",
            "// Sequência de Fibonacci (Calcula F5 = 5)\n" +
            "LOCO 1\n" +
            "STOD 30      // F1 = 1\n" +
            "STOD 31      // F2 = 1\n" +
            "LOCO 3\n" +
            "STOD 40      // Contador = 3 iterações\n" +
            "LOOP: LODD 40\n" +
            "JZER FIM\n" +
            "SUBD 20      // Decrementa contador (DATA 1 no end. 20)\n" +
            "STOD 40\n" +
            "LODD 30\n" +
            "ADDD 31\n" +
            "STOD 32      // Próximo termo\n" +
            "LODD 31\n" +
            "STOD 30\n" +
            "LODD 32\n" +
            "STOD 31\n" +
            "JUMP LOOP\n" +
            "FIM: LODD 32 // Carrega F5 (5) no AC\n" +
            "STOD 33\n" +
            "HALT\n" +
            "DATA 1\n");

        bibliotecaProgramas.put("4. Fatorial (4! = 24)",
            "// Macroprograma: Fatorial de 4 (4! = 24 = 0x0018)\n" +
            "LOCO 4              // [00] AC = 4\n" +
            "STOD 30             // [01] Mem[30] = 4 (N)\n" +
            "LOCO 1              // [02] AC = 1\n" +
            "STOD 31             // [03] Mem[31] = 1 (resultado)\n" +
            "FAT_LOOP: LODD 30   // [04] Carrega N\n" +
            "JZER FIM            // [05] Se N == 0, vai para [26]\n" +
            "SUBD 28             // [06] N - 1 (lê DATA 1 do end. 28)\n" +
            "JZER FIM            // [07] Se N == 1, vai para [26]\n" +
            "LODD 30             // [08] AC = N\n" +
            "STOD 32             // [09] Mem[32] = N (contador da soma)\n" +
            "LOCO 0              // [10] AC = 0\n" +
            "STOD 33             // [11] Mem[33] = 0 (soma parcial)\n" +
            "MULT_LOOP: LODD 32  // [12] Carrega contador da soma\n" +
            "JZER FIM_MULT       // [13] Se contador == 0, vai para [20]\n" +
            "SUBD 28             // [14] Decrementa contador (-1)\n" +
            "STOD 32             // [15] Salva contador\n" +
            "LODD 33             // [16] Carrega soma parcial\n" +
            "ADDD 31             // [17] Soma resultado acumulado\n" +
            "STOD 33             // [18] Salva soma parcial\n" +
            "JUMP MULT_LOOP      // [19] Volta para [12]\n" +
            "FIM_MULT: LODD 33   // [20] Carrega produto parcial\n" +
            "STOD 31             // [21] Atualiza resultado do fatorial\n" +
            "LODD 30             // [22] Carrega N\n" +
            "SUBD 28             // [23] N = N - 1\n" +
            "STOD 30             // [24] Salva novo N\n" +
            "JUMP FAT_LOOP       // [25] Volta para [04]\n" +
            "FIM: LODD 31        // [26] Carrega resultado final (24) no AC\n" +
            "HALT                // [27] Encerra execução\n" +
            "DATA 1              // [28] Constante 1\n");
    }

    private void configurarJanela() {
        setTitle("Simulador MIC-1 — Arquitetura & Microprogramação");
        setSize(1280, 820);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(COR_FUNDO);
        setLayout(new BorderLayout(10, 10));
    }

    private void inicializarComponentes() {
        // ================= BARRA DE STATUS SUPERIOR =================
        JPanel barraTopo = new JPanel(new BorderLayout(15, 5));
        barraTopo.setBackground(new Color(18, 20, 26));
        barraTopo.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        lblInstrucaoAtual = new JLabel("Instrução em Execução (IR): 0x0000 (NOP / Aguardando)");
        lblInstrucaoAtual.setFont(new Font("SansSerif", Font.BOLD, 15));
        lblInstrucaoAtual.setForeground(new Color(121, 192, 255));

        lblStatusCiclos = new JLabel("Ciclos: 0  |  MPC: 0x00  |  MAR: 0x00  |  MBR: 0x0000");
        lblStatusCiclos.setFont(new Font("Monospaced", Font.BOLD, 13));
        lblStatusCiclos.setForeground(new Color(126, 231, 135));

        barraTopo.add(lblInstrucaoAtual, BorderLayout.WEST);
        barraTopo.add(lblStatusCiclos, BorderLayout.EAST);
        add(barraTopo, BorderLayout.NORTH);

        // ================= ÁREA CENTRAL (SUPERIOR + INFERIOR) =================
        JPanel painelPrincipal = new JPanel(new BorderLayout(10, 10));
        painelPrincipal.setBackground(COR_FUNDO);
        painelPrincipal.setBorder(BorderFactory.createEmptyBorder(5, 12, 12, 12));

        // --- METADE SUPERIOR: Esquerda (Assembly + Compilado + Botões) | Direita (Registradores + Microinstrução) ---
        JPanel metadeSuperior = new JPanel(new GridLayout(1, 2, 12, 0));
        metadeSuperior.setBackground(COR_FUNDO);

        // LADO ESQUERDO
        JPanel ladoEsquerdo = new JPanel(new BorderLayout(8, 8));
        ladoEsquerdo.setBackground(COR_FUNDO);

        // Barra de Gerenciamento de Códigos (Seletor + Salvar na Lista + Abrir/Salvar Arquivo)
        JPanel barraBiblioteca = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        barraBiblioteca.setBackground(COR_PAINEL);
        barraBiblioteca.setBorder(BorderFactory.createLineBorder(new Color(55, 62, 75), 1));

        JLabel lblProg = new JLabel("Programa:");
        lblProg.setForeground(COR_TEXTO);
        lblProg.setFont(new Font("SansSerif", Font.BOLD, 12));

        comboProgramas = new JComboBox<>(bibliotecaProgramas.keySet().toArray(new String[0]));
        comboProgramas.setPreferredSize(new Dimension(210, 26));
        comboProgramas.setFont(new Font("SansSerif", Font.PLAIN, 12));

        JButton btnSalvarLista = criarBotao("+ Na Lista", new Color(31, 111, 235));
        JButton btnSalvarArq = criarBotao("Salvar .asm", new Color(75, 85, 99));
        JButton btnAbrirArq = criarBotao("Abrir .asm", new Color(75, 85, 99));

        barraBiblioteca.add(lblProg);
        barraBiblioteca.add(comboProgramas);
        barraBiblioteca.add(btnSalvarLista);
        barraBiblioteca.add(btnSalvarArq);
        barraBiblioteca.add(btnAbrirArq);

        JPanel painelEditores = new JPanel(new GridLayout(1, 2, 8, 0));
        painelEditores.setBackground(COR_FUNDO);

        // Campo Assembly (Macroprograma)
        String primeiroCodigo = bibliotecaProgramas.values().iterator().next();
        txtAssembly = criarAreaTexto(primeiroCodigo, true, new Color(255, 255, 255));
        JScrollPane scrollAssembly = criarScrollComTitulo(txtAssembly, "Assembly (Macroprograma):", COR_DESTAQUE_AZUL);

        // Campo Compilado
        txtCompilado = criarAreaTexto("", false, new Color(165, 214, 255));
        JScrollPane scrollCompilado = criarScrollComTitulo(txtCompilado, "Compilado (16-bit):", COR_DESTAQUE_ROXO);

        painelEditores.add(scrollAssembly);
        painelEditores.add(scrollCompilado);

        // Painel de Controles (Montar, Pausa, Gravar na MP, Iniciar, Passo)
        JPanel painelControles = new JPanel(new GridLayout(2, 1, 6, 6));
        painelControles.setBackground(COR_PAINEL);
        painelControles.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel linhaCtrl1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        linhaCtrl1.setBackground(COR_PAINEL);

        JButton btnMontar = criarBotao("1. Montar", COR_DESTAQUE_AZUL);
        JLabel lblPausa = new JLabel("Pausa (ms):");
        lblPausa.setForeground(COR_TEXTO);
        spinnerPausa = new JSpinner(new SpinnerNumberModel(150, 0, 5000, 50));
        spinnerPausa.setPreferredSize(new Dimension(70, 28));
        JButton btnAplicar = criarBotao("Aplicar", new Color(75, 85, 99));

        linhaCtrl1.add(btnMontar);
        linhaCtrl1.add(lblPausa);
        linhaCtrl1.add(spinnerPausa);
        linhaCtrl1.add(btnAplicar);

        JPanel linhaCtrl2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        linhaCtrl2.setBackground(COR_PAINEL);

        JButton btnGravarMP = criarBotao("2. Gravar código na MP", COR_DESTAQUE_ROXO);
        btnIniciar = criarBotao("3. Iniciar", COR_DESTAQUE_VERDE);
        JButton btnPasso = criarBotao("Passo (1 Ciclo)", COR_DESTAQUE_LARANJA);
        JButton btnReset = criarBotao("Reset", new Color(218, 54, 51));

        linhaCtrl2.add(btnGravarMP);
        linhaCtrl2.add(btnIniciar);
        linhaCtrl2.add(btnPasso);
        linhaCtrl2.add(btnReset);

        painelControles.add(linhaCtrl1);
        painelControles.add(linhaCtrl2);

        ladoEsquerdo.add(barraBiblioteca, BorderLayout.NORTH);
        ladoEsquerdo.add(painelEditores, BorderLayout.CENTER);
        ladoEsquerdo.add(painelControles, BorderLayout.SOUTH);

        // LADO DIREITO
        JPanel ladoDireito = new JPanel(new BorderLayout(8, 8));
        ladoDireito.setBackground(COR_FUNDO);

        // Tabela de Registradores
        modeloRegs = new DefaultTableModel(new Object[]{"Registrador", "Valor (Hex)", "Valor (Dec)"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tabelaRegistradores = criarTabelaEstilizada(modeloRegs);
        JScrollPane scrollRegs = criarScrollComTitulo(tabelaRegistradores, "Registradores:", COR_DESTAQUE_VERDE);

        // Painel Microinstrução Atual
        txtMicroinstrucao = criarAreaTexto("Nenhuma microinstrução executada ainda.", false, new Color(255, 223, 130));
        txtMicroinstrucao.setLineWrap(true);
        txtMicroinstrucao.setWrapStyleWord(true);
        txtMicroinstrucao.setRows(4);
        JScrollPane scrollMicro = criarScrollComTitulo(txtMicroinstrucao, "Microinstrução em Execução (MIR):", COR_DESTAQUE_LARANJA);

        ladoDireito.add(scrollRegs, BorderLayout.CENTER);
        ladoDireito.add(scrollMicro, BorderLayout.SOUTH);

        metadeSuperior.add(ladoEsquerdo);
        metadeSuperior.add(ladoDireito);

        // --- METADE INFERIOR: Abas para Memória Principal (MP) e Memória Cache ---
        JTabbedPane abasMemoria = new JTabbedPane();
        abasMemoria.setFont(new Font("SansSerif", Font.BOLD, 13));
        abasMemoria.setBackground(COR_PAINEL);
        abasMemoria.setForeground(Color.WHITE);

        // Aba 1: Memória Principal
        modeloMP = new DefaultTableModel(new Object[]{"Endereço", "Valor (binário)", "Valor (decimal)", "Hex", "Decodificação"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tabelaMP = criarTabelaEstilizada(modeloMP);
        configurarDestaqueLinhaPC(tabelaMP);
        abasMemoria.addTab("Memória Principal (MP)", new JScrollPane(tabelaMP));

        // Aba 2: Memória Cache
        modeloCache = new DefaultTableModel(new Object[]{"Linha", "Válido", "Dirty", "Tag", "Palavra [0]", "Palavra [1]", "Palavra [2]", "Palavra [3]"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tabelaCache = criarTabelaEstilizada(modeloCache);
        abasMemoria.addTab("Memória Cache (Mapeamento Direto)", new JScrollPane(tabelaCache));

        painelDatapath = new PainelDatapath(processador);
        abasMemoria.addTab("Caminho de Dados (Datapath Visual)", painelDatapath);

        JSplitPane splitVertical = new JSplitPane(JSplitPane.VERTICAL_SPLIT, metadeSuperior, abasMemoria);
        splitVertical.setResizeWeight(0.56);
        splitVertical.setDividerSize(6);
        splitVertical.setBackground(COR_FUNDO);

        painelPrincipal.add(splitVertical, BorderLayout.CENTER);
        add(painelPrincipal, BorderLayout.CENTER);

        // ================= EVENTOS DA BIBLIOTECA DE PROGRAMAS =================
        comboProgramas.addActionListener(e -> {
            String selecionado = (String) comboProgramas.getSelectedItem();
            if (selecionado != null && bibliotecaProgramas.containsKey(selecionado)) {
                pararTimer();
                txtAssembly.setText(bibliotecaProgramas.get(selecionado));
                txtCompilado.setText("");
                ultimoResultado = null;
            }
        });

        btnSalvarLista.addActionListener(e -> {
            String nome = JOptionPane.showInputDialog(this, "Nome para este programa na lista:", "Novo Programa", JOptionPane.PLAIN_MESSAGE);
            if (nome != null && !nome.trim().isEmpty()) {
                nome = nome.trim();
                boolean jaExistia = bibliotecaProgramas.containsKey(nome);
                bibliotecaProgramas.put(nome, txtAssembly.getText());
                if (!jaExistia) {
                    comboProgramas.addItem(nome);
                }
                comboProgramas.setSelectedItem(nome);
            }
        });

        btnSalvarArq.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Salvar Macroprograma Assembly");
            chooser.setFileFilter(new FileNameExtensionFilter("Arquivos Assembly/Texto (*.asm, *.txt)", "asm", "txt"));
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File arquivo = chooser.getSelectedFile();
                if (!arquivo.getName().contains(".")) {
                    arquivo = new File(arquivo.getAbsolutePath() + ".asm");
                }
                try {
                    Files.writeString(arquivo.toPath(), txtAssembly.getText());
                    JOptionPane.showMessageDialog(this, "Arquivo salvo com sucesso!");
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "Erro ao salvar: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        btnAbrirArq.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Abrir Macroprograma Assembly");
            chooser.setFileFilter(new FileNameExtensionFilter("Arquivos Assembly/Texto (*.asm, *.txt)", "asm", "txt"));
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File arquivo = chooser.getSelectedFile();
                try {
                    String conteudo = Files.readString(arquivo.toPath());
                    String nomeItem = arquivo.getName();
                    boolean jaExistia = bibliotecaProgramas.containsKey(nomeItem);
                    bibliotecaProgramas.put(nomeItem, conteudo);
                    if (!jaExistia) {
                        comboProgramas.addItem(nomeItem);
                    }
                    comboProgramas.setSelectedItem(nomeItem);
                    txtAssembly.setText(conteudo);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "Erro ao abrir: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        // ================= TIMER & EVENTOS DE EXECUÇÃO =================
        timerExecucao = new Timer((int) spinnerPausa.getValue(), e -> {
            if (!processador.isFinalizado()) {
                processador.executarCiclo();
                atualizarTela();
            } else {
                pararTimer();
                atualizarTela();
            }
        });

        btnMontar.addActionListener(e -> {
            try {
                ultimoResultado = montadorMacro.montar(txtAssembly.getText());
                txtCompilado.setText(String.join("\n", ultimoResultado.linhasBinarias));
                txtMicroinstrucao.setText("Assembly montado com sucesso! Clique em 'Gravar código na MP'.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Erro de Montagem", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnGravarMP.addActionListener(e -> {
            if (ultimoResultado == null) {
                btnMontar.doClick();
            }
            if (ultimoResultado != null) {
                pararTimer();
                processador.carregarProgramaNaMP(ultimoResultado.palavrasMemoria);
                txtMicroinstrucao.setText("Código gravado na Memória Principal! Clique em 'Iniciar' ou 'Passo'.");
                atualizarTela();
            }
        });

        btnAplicar.addActionListener(e -> {
            int delay = (int) spinnerPausa.getValue();
            timerExecucao.setDelay(delay);
        });

        btnIniciar.addActionListener(e -> {
            if (timerExecucao.isRunning()) {
                pararTimer();
            } else {
                timerExecucao.setDelay((int) spinnerPausa.getValue());
                timerExecucao.start();
                btnIniciar.setText("Pausar");
                btnIniciar.setBackground(COR_DESTAQUE_LARANJA);
            }
        });

        btnPasso.addActionListener(e -> {
            pararTimer();
            processador.executarCiclo();
            atualizarTela();
        });

        btnReset.addActionListener(e -> {
            pararTimer();
            processador.resetar();
            ultimoResultado = null;
            txtCompilado.setText("");
            txtMicroinstrucao.setText("Simulador reiniciado.");
            atualizarTela();
        });
    }

    private void pararTimer() {
        if (timerExecucao.isRunning()) {
            timerExecucao.stop();
        }
        btnIniciar.setText("3. Iniciar");
        btnIniciar.setBackground(COR_DESTAQUE_VERDE);
    }

    private void atualizarTela() {
        int pc = processador.getMapaRegistradores().get("PC");
        int ir = processador.getMapaRegistradores().get("IR");

        lblInstrucaoAtual.setText(String.format("Instrução Atual (IR): 0x%04X  →  %s", ir, decodificarInstrucaoTexto(ir)));
        lblStatusCiclos.setText(String.format(
            "Ciclos: %d  |  MPC: 0x%02X  |  MAR: 0x%02X  |  MBR: 0x%04X",
            processador.getCiclosExecutados(),
            processador.getMpc().getValor(),
            processador.getMar().getEndereco(),
            processador.getMbr().getValor()
        ));

        txtMicroinstrucao.setText(processador.getDescricaoMicroinstrucaoAtual());

        // 1. Atualiza Registradores
        modeloRegs.setRowCount(0);
        processador.getMapaRegistradores().forEach((nome, val) -> {
            modeloRegs.addRow(new Object[]{nome, String.format("0x%04X", val), (short) val.intValue()});
        });

        // 2. Atualiza Memória Principal (primeiras 64 posições)
        modeloMP.setRowCount(0);
        Memoria mp = processador.getMemoriaPrincipal();
        for (int i = 0; i < 64; i++) {
            int val = mp.leitura(i) & 0xFFFF;
            String bin16 = String.format("%16s", Integer.toBinaryString(val)).replace(' ', '0');
            modeloMP.addRow(new Object[]{
                i,
                bin16,
                (short) val,
                String.format("0x%04X", val),
                decodificarInstrucaoTexto(val)
            });
        }

        // 3. Atualiza Cache
        modeloCache.setRowCount(0);
        Cache cache = processador.getCache();
        for (int i = 0; i < cache.getQuantidadeLinhas(); i++) {
            boolean valido = cache.isValidoLinha(i);
            boolean dirty = cache.isDirtyLinha(i);
            int tag = cache.getTagLinha(i);
            int[] dados = cache.getDadosLinha(i);

            modeloCache.addRow(new Object[]{
                "Linha " + i,
                valido ? "SIM (1)" : "NÃO (0)",
                dirty ? "SIM (Modificado)" : "NÃO",
                valido ? String.format("0x%02X (%d)", tag, tag) : "-",
                String.format("0x%04X", dados[0] & 0xFFFF),
                String.format("0x%04X", dados[1] & 0xFFFF),
                String.format("0x%04X", dados[2] & 0xFFFF),
                String.format("0x%04X", dados[3] & 0xFFFF)
            });
        }

        tabelaMP.repaint();

        if (painelDatapath != null) painelDatapath.repaint();
    }

    private String decodificarInstrucaoTexto(int palavra) {
        int val = palavra & 0xFFFF;
        if (val == 0x0000) return "LODD 0 / NOP";
        if (val == 0xFFFF) return "HALT";
        int op4 = (val >>> 12) & 0xF;
        int end12 = val & 0x0FFF;
        switch (op4) {
            case 0x0: return "LODD " + end12;
            case 0x1: return "STOD " + end12;
            case 0x2: return "ADDD " + end12;
            case 0x3: return "SUBD " + end12;
            case 0x4: return "JPOS " + end12;
            case 0x5: return "JZER " + end12;
            case 0x6: return "JUMP " + end12;
            case 0x7: return "LOCO " + end12;
            case 0x8: return "LODL " + end12;
            case 0x9: return "STOL " + end12;
            case 0xA: return "ADDL " + end12;
            case 0xB: return "SUBL " + end12;
            case 0xC: return "JNEG " + end12;
            case 0xD: return "JNZE " + end12;
            case 0xE: return "CALL " + end12;
            default:
                int y = val & 0x00FF;
                switch ((val >>> 9) & 0x7) {
                    case 0: return "PSHI";
                    case 1: return "POPI";
                    case 2: return "PUSH";
                    case 3: return "POP";
                    case 4: return "RETN";
                    case 5: return "SWAP";
                    case 6: return "INSP " + y;
                    default: return "DESP " + y;
                }
        }
    }

    // ================= HELPERS DE ESTILO COLORIDO =================
    private JTextArea criarAreaTexto(String texto, boolean editavel, Color corTexto) {
        JTextArea area = new JTextArea(texto);
        area.setEditable(editavel);
        area.setBackground(COR_PAINEL);
        area.setForeground(corTexto);
        area.setCaretColor(Color.WHITE);
        area.setFont(new Font("Monospaced", Font.PLAIN, 13));
        area.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        return area;
    }

    private JScrollPane criarScrollComTitulo(Component comp, String titulo, Color corBorda) {
        JScrollPane scroll = new JScrollPane(comp);
        scroll.getViewport().setBackground(COR_PAINEL);
        TitledBorder borda = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(corBorda, 2), titulo
        );
        borda.setTitleColor(corBorda.brighter());
        borda.setTitleFont(new Font("SansSerif", Font.BOLD, 13));
        scroll.setBorder(borda);
        scroll.setBackground(COR_FUNDO);
        return scroll;
    }

    private JButton criarBotao(String texto, Color corFundo) {
        JButton btn = new JButton(texto);
        btn.setBackground(corFundo);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JTable criarTabelaEstilizada(DefaultTableModel modelo) {
        JTable tabela = new JTable(modelo);
        tabela.setBackground(COR_PAINEL);
        tabela.setForeground(COR_TEXTO);
        tabela.setGridColor(new Color(55, 62, 75));
        tabela.setRowHeight(23);
        tabela.setFont(new Font("Monospaced", Font.PLAIN, 12));
        tabela.getTableHeader().setBackground(new Color(20, 23, 30));
        tabela.getTableHeader().setForeground(new Color(147, 197, 253));
        tabela.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        return tabela;
    }

    private void configurarDestaqueLinhaPC(JTable tabela) {
        tabela.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                int pcAtual = processador.getMapaRegistradores().get("PC");
                if (row == pcAtual) {
                    c.setBackground(COR_LINHA_ATIVA);
                    c.setForeground(Color.WHITE);
                } else {
                    c.setBackground(row % 2 == 0 ? COR_PAINEL : new Color(38, 43, 54));
                    c.setForeground(COR_TEXTO);
                }
                return c;
            }
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Interface().setVisible(true));
    }
}