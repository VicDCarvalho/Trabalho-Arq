import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.Map;

public class PainelDatapath extends JPanel {
    private final Processador processador;

    private static final Color FUNDO = new Color(18, 21, 28);
    private static final Color GRADE = new Color(28, 32, 42);
    private static final Color BLOCO_FUNDO = new Color(30, 35, 46);
    private static final Color BLOCO_BORDA = new Color(70, 82, 102);
    private static final Color ATIVO_AZUL = new Color(56, 189, 248);
    private static final Color ATIVO_VERDE = new Color(74, 222, 128);
    private static final Color ATIVO_LARANJA = new Color(251, 191, 36);
    private static final Color ATIVO_ROXO = new Color(192, 132, 252);
    private static final Color INATIVO_LINHA = new Color(58, 66, 82);

    // Canvas virtual fixo (1200 x 340) que se adapta a qualquer tamanho de janela
    private static final double BASE_W = 1200.0;
    private static final double BASE_H = 340.0;

    public PainelDatapath(Processador processador) {
        this.processador = processador;
        setBackground(FUNDO);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Fundo quadriculado sutil estilo diagrama de hardware
        g2.setColor(GRADE);
        for (int gx = 0; gx < getWidth(); gx += 24) g2.drawLine(gx, 0, gx, getHeight());
        for (int gy = 0; gy < getHeight(); gy += 24) g2.drawLine(0, gy, getWidth(), gy);

        // Escala proporcional mantendo centralização e proporção sem cortar nada
        double escalaX = getWidth() / BASE_W;
        double escalaY = getHeight() / BASE_H;
        double escala = Math.min(escalaX, escalaY);
        double offsetX = (getWidth() - BASE_W * escala) / 2.0;
        double offsetY = (getHeight() - BASE_H * escala) / 2.0;

        g2.translate(offsetX, offsetY);
        g2.scale(escala, escala);

        MIR mir = processador.getMir();
        Map<String, Integer> regs = processador.getMapaRegistradores();

        boolean amuxUsaMBR = (mir.getAMUX() == 0);
        boolean marAtivo = (mir.getMAR() == 1);
        boolean mbrAtivo = (mir.getMBR() == 1);
        boolean rdAtivo = (mir.getRD() == 1);
        boolean wrAtivo = (mir.getWR() == 1);
        boolean encAtivo = (mir.getENC() == 1);

        // ================= 1. BARRAMENTOS (DESENHADOS ATRÁS DOS BLOCOS) =================
        // Barramento C (Saída Deslocador -> Entrada esquerda do Banco de Registradores)
        Color corBusC = encAtivo ? ATIVO_VERDE : INATIVO_LINHA;
        desenharTrilhaOrtogonal(g2, corBusC, encAtivo,
            new int[]{580, 335, 335, 390},
            new int[]{302, 302, 65, 65}
        );
        desenharEtiqueta(g2, encAtivo ? "Bus C → reg[" + mir.getC() + "]" : "Bus C", 250, 185, corBusC);

        // Barramento A (Saída inferior esquerda Regs -> Entrada esquerda AMUX)
        Color corBusA = !amuxUsaMBR ? ATIVO_AZUL : INATIVO_LINHA;
        desenharTrilhaOrtogonal(g2, corBusA, !amuxUsaMBR,
            new int[]{460, 460},
            new int[]{125, 162}
        );
        desenharEtiqueta(g2, "Bus A [" + mir.getA() + "]", 395, 148, corBusA);

        // Barramento B (Saída inferior direita Regs -> Entrada direita ULA)
        desenharTrilhaOrtogonal(g2, ATIVO_AZUL, true,
            new int[]{670, 670},
            new int[]{125, 222}
        );
        desenharEtiqueta(g2, "Bus B [" + mir.getB() + "]", 680, 175, ATIVO_AZUL);

        // Derivação Bus B -> MAR
        Color corMarBus = marAtivo ? ATIVO_LARANJA : INATIVO_LINHA;
        desenharTrilhaOrtogonal(g2, corMarBus, marAtivo,
            new int[]{670, 880},
            new int[]{142, 62}
        );
        if (marAtivo) desenharEtiqueta(g2, "MAR := B", 765, 92, ATIVO_LARANJA);

        // AMUX -> Entrada esquerda da ULA
        desenharTrilhaOrtogonal(g2, ATIVO_AZUL, true,
            new int[]{490, 490},
            new int[]{206, 222}
        );

        // MBR -> Entrada direita do AMUX
        Color corMbrAmux = amuxUsaMBR ? ATIVO_AZUL : INATIVO_LINHA;
        desenharTrilhaOrtogonal(g2, corMbrAmux, amuxUsaMBR,
            new int[]{880, 580},
            new int[]{172, 184}
        );
        if (amuxUsaMBR) desenharEtiqueta(g2, "MBR → AMUX", 730, 166, ATIVO_AZUL);

        // Saída ULA/Deslocador -> MBR (quando WR/STOD grava no MBR)
        Color corUlaMbr = mbrAtivo ? ATIVO_LARANJA : INATIVO_LINHA;
        desenharTrilhaOrtogonal(g2, corUlaMbr, mbrAtivo,
            new int[]{735, 810, 810, 880},
            new int[]{285, 285, 192, 192}
        );
        if (mbrAtivo) desenharEtiqueta(g2, "MBR := ULA", 745, 276, ATIVO_LARANJA);

        // Conexão MAR/MBR <-> Cache / Memória Principal
        Color corMem = rdAtivo ? ATIVO_AZUL : (wrAtivo ? ATIVO_LARANJA : INATIVO_LINHA);
        desenharTrilhaOrtogonal(g2, corMem, (rdAtivo || wrAtivo),
            new int[]{1020, 1020},
            new int[]{215, 245}
        );
        if (rdAtivo || wrAtivo) {
            desenharEtiqueta(g2, rdAtivo ? "RD=1 (Leitura)" : "WR=1 (Escrita)", 1030, 234, corMem);
        }

        // ================= 2. BLOCOS DO PROCESSADOR =================
        // Bloco 1: Unidade de Controle (Esquerda)
        desenharBloco(g2, 25, 22, 245, 130, "UNIDADE DE CONTROLE (UC)", ATIVO_ROXO,
            String.format("MPC: 0x%02X (%d)   |   COND: %d", processador.getMpc().getValor(), processador.getMpc().getValor(), mir.getCOND()),
            String.format("ALU: %d   |   AMUX: %d   |   SH: %d", mir.getALU(), mir.getAMUX(), mir.getSH()),
            String.format("RD=%d  WR=%d  MAR=%d  MBR=%d  ENC=%d", mir.getRD(), mir.getWR(), mir.getMAR(), mir.getMBR(), mir.getENC()),
            String.format("ADDR (Próximo): 0x%02X", mir.getADDR())
        );

        // Legenda de Cores (Esquerda-Inferior)
        desenharBloco(g2, 25, 175, 245, 140, "LEGENDA DE SINAIS ATIVOS", BLOCO_BORDA,
            "● Azul: Fluxo de Dados / Leitura",
            "● Verde: Escrita no Banco (ENC=1)",
            "● Laranja: ULA / Escrita MAR-MBR",
            "● Roxo: Controle / Sequenciador"
        );

        // Bloco 2: Banco de Registradores (Centro-Topo)
        desenharBloco(g2, 390, 20, 345, 105, "BANCO DE REGISTRADORES (16 x 16-bit)", encAtivo ? ATIVO_VERDE : ATIVO_AZUL,
            String.format("PC: 0x%04X (%d)    |    AC: 0x%04X (%d)",
                regs.get("PC"), regs.get("PC"), regs.get("AC"), (short) regs.get("AC").intValue()),
            String.format("IR: 0x%04X         |    TIR: 0x%04X", regs.get("IR"), regs.get("TIR")),
            String.format("SP: 0x%04X         |    AMASK: 0x%04X", regs.get("SP"), regs.get("AMASK"))
        );

        // Bloco 3: AMUX (Centro-Esquerda)
        desenharBloco(g2, 415, 162, 165, 44, "AMUX (" + (amuxUsaMBR ? "0: MBR" : "1: Bus A") + ")", ATIVO_AZUL,
            amuxUsaMBR ? "Selecionado: MBR" : "Selecionado: Latch A"
        );

        // Bloco 4: ULA + Deslocador (Centro-Inferior)
        String opUla = switch (mir.getALU()) {
            case 0 -> "0: A + B (Soma)";
            case 1 -> "1: A AND B";
            case 2 -> "2: A OR B";
            case 3 -> "3: NOT A";
            default -> "4: PASSA A";
        };
        String opShift = switch (mir.getSH()) {
            case 1 -> "Dir (>>1)";
            case 2 -> "Esq (<<1)";
            default -> "Sem Desloc. (0)";
        };
        desenharBloco(g2, 415, 222, 320, 93, "ULA & DESLOCADOR", ATIVO_LARANJA,
            "Operação ULA: " + opUla,
            "Deslocador: " + opShift,
            encAtivo ? String.format("Saída → Gravando reg[%d]", mir.getC()) : "Saída → Apenas Flags / MBR"
        );

        // Bloco 5: MAR (Direita-Topo)
        desenharBloco(g2, 880, 22, 290, 75, "MAR (Memory Address Reg.)", marAtivo ? ATIVO_LARANJA : BLOCO_BORDA,
            String.format("Endereço: 0x%03X (%d)", processador.getMar().getEndereco(), processador.getMar().getEndereco()),
            marAtivo ? "Status: Carregando endereço" : "Status: Mantendo endereço"
        );

        // Bloco 6: MBR (Direita-Meio)
        desenharBloco(g2, 880, 125, 290, 90, "MBR (Memory Buffer Reg.)", (mbrAtivo || rdAtivo) ? ATIVO_AZUL : BLOCO_BORDA,
            String.format("Valor: 0x%04X (%d)", processador.getMbr().getValor(), (short) processador.getMbr().getValor()),
            mbrAtivo ? "Origem: Saída ULA" : (rdAtivo ? "Origem: Cache / MP" : "Status: Em espera")
        );

        // Bloco 7: Cache & Memória Principal (Direita-Inferior)
        String statusMem = rdAtivo ? "LENDO DADO (RD=1)" : (wrAtivo ? "GRAVANDO DADO (WR=1)" : "Aguardando sinal");
        desenharBloco(g2, 880, 245, 290, 72, "CACHE & MEMÓRIA PRINCIPAL", corMem,
            "Estado: " + statusMem,
            "Cache Direta (8 linhas x 4 palavras)"
        );
    }

    private void desenharBloco(Graphics2D g2, int x, int y, int w, int h, String titulo, Color borda, String... linhas) {
        // Sombra suave
        g2.setColor(new Color(10, 12, 16, 150));
        g2.fill(new RoundRectangle2D.Double(x + 3, y + 3, w, h, 10, 10));

        // Corpo do bloco
        g2.setColor(BLOCO_FUNDO);
        g2.fill(new RoundRectangle2D.Double(x, y, w, h, 10, 10));

        // Cabeçalho colorido sutil
        g2.setColor(new Color(borda.getRed(), borda.getGreen(), borda.getBlue(), 35));
        g2.fill(new RoundRectangle2D.Double(x, y, w, 24, 10, 10));

        // Borda
        g2.setStroke(new BasicStroke(borda == BLOCO_BORDA ? 1.5f : 2.3f));
        g2.setColor(borda);
        g2.draw(new RoundRectangle2D.Double(x, y, w, h, 10, 10));

        // Título
        g2.setFont(new Font("SansSerif", Font.BOLD, 11));
        g2.setColor(borda == BLOCO_BORDA ? new Color(190, 205, 230) : borda.brighter());
        g2.drawString(titulo, x + 10, y + 16);

        // Linhas de conteúdo
        g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
        g2.setColor(new Color(235, 242, 252));
        for (int i = 0; i < linhas.length; i++) {
            g2.drawString(linhas[i], x + 10, y + 38 + (i * 17));
        }
    }

    private void desenharTrilhaOrtogonal(Graphics2D g2, Color cor, boolean ativo, int[] xs, int[] ys) {
        g2.setStroke(new BasicStroke(ativo ? 2.6f : 1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(cor);
        g2.drawPolyline(xs, ys, xs.length);

        // Ponta circular no destino
        int xf = xs[xs.length - 1];
        int yf = ys[ys.length - 1];
        g2.fillOval(xf - 4, yf - 4, 8, 8);
    }

    private void desenharEtiqueta(Graphics2D g2, String texto, int x, int y, Color cor) {
        g2.setFont(new Font("SansSerif", Font.BOLD, 10));
        FontMetrics fm = g2.getFontMetrics();
        int pad = 4;
        g2.setColor(new Color(18, 21, 28, 220));
        g2.fillRoundRect(x - pad, y - fm.getAscent() - 1, fm.stringWidth(texto) + pad * 2, fm.getHeight() + 2, 6, 6);
        g2.setColor(cor);
        g2.drawString(texto, x, y);
    }
}