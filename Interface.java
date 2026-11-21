import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

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

    private JSpinner spinnerPausa;
    private Timer timerExecucao;
    private JButton btnIniciar;

    public Interface() {
        this.processador = new Processador();
        this.montadorMacro = new MontadorMacro();
        configurarJanela();
        inicializarComponentes();
        atualizarTela();
    }

    private void configurarJanela() {
        setTitle("Simulador MIC-1 — Arquitetura & Microprogramação");
        setSize(1280, 800);
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

        JPanel painelEditores = new JPanel(new GridLayout(1, 2, 8, 0));
        painelEditores.setBackground(COR_FUNDO);

        // Campo Assembly (Macroprograma)
        String exemploAssembly =
            "// Macroprograma de Exemplo (Soma 10 + 25)\n" +
            "LOCO 10\n" +
            "STOD 20      // Guarda 10 no end. 20\n" +
            "LOCO 25\n" +
            "ADDD 20      // AC = 25 + 10 (35)\n" +
            "STOD 21      // Guarda resultado no end. 21\n" +
            "HALT         // Encerra execução\n";

        txtAssembly = criarAreaTexto(exemploAssembly, true, new Color(255, 255, 255));
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

        // Aba 1: Memória Principal (igual à imagem: Endereço | Binário | Decimal + Hex/Instrução)
        modeloMP = new DefaultTableModel(new Object[]{"Endereço", "Valor (binário)", "Valor (decimal)", "Hex", "Decodificação"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tabelaMP = criarTabelaEstilizada(modeloMP);
        configurarDestaqueLinhaPC(tabelaMP);
        abasMemoria.addTab("Memória Principal (MP)", new JScrollPane(tabelaMP));

        // Aba 2: Memória Cache (Linhas, Válido, Dirty, Tag e Blocos)
        modeloCache = new DefaultTableModel(new Object[]{"Linha", "Válido", "Dirty", "Tag", "Palavra [0]", "Palavra [1]", "Palavra [2]", "Palavra [3]"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tabelaCache = criarTabelaEstilizada(modeloCache);
        abasMemoria.addTab("Memória Cache (Mapeamento Direto)", new JScrollPane(tabelaCache));

        JSplitPane splitVertical = new JSplitPane(JSplitPane.VERTICAL_SPLIT, metadeSuperior, abasMemoria);
        splitVertical.setResizeWeight(0.56);
        splitVertical.setDividerSize(6);
        splitVertical.setBackground(COR_FUNDO);

        painelPrincipal.add(splitVertical, BorderLayout.CENTER);
        add(painelPrincipal, BorderLayout.CENTER);

        // ================= TIMER & EVENTOS =================
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

        // 2. Atualiza Memória Principal (primeiras 64 posições para performance visual)
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
            case 0xC: return "JNEG " + end12;
            default:  return "Dado / Estendida";
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