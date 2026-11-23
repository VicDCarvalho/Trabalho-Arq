import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.Map;

public class PainelDatapath extends JPanel {
    private final Processador processador;

    private static final Color FUNDO = new Color(22, 25, 32);
    private static final Color BLOCO_FUNDO = new Color(34, 39, 50);
    private static final Color BLOCO_BORDA = new Color(75, 85, 105);
    private static final Color ATIVO_AZUL = new Color(56, 189, 248);
    private static final Color ATIVO_VERDE = new Color(74, 222, 128);
    private static final Color ATIVO_LARANJA = new Color(251, 191, 36);
    private static final Color ATIVO_ROXO = new Color(192, 132, 252);
    private static final Color INATIVO_LINHA = new Color(60, 68, 82);

    public PainelDatapath(Processador processador) {
        this.processador = processador;
        setBackground(FUNDO);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        MIR mir = processador.getMir();
        Map<String, Integer> regs = processador.getMapaRegistradores();

        boolean amuxUsaMBR = (mir.getAMUX() == 0);
        boolean marAtivo = (mir.getMAR() == 1);
        boolean mbrAtivo = (mir.getMBR() == 1);
        boolean rdAtivo = (mir.getRD() == 1);
        boolean wrAtivo = (mir.getWR() == 1);
        boolean encAtivo = (mir.getENC() == 1);

        int w = getWidth();
        int h = getHeight();
        int cx = w / 2;

        // 1. UNIDADE DE CONTROLE (Esquerda)
        desenharBloco(g2, 30, 25, 220, 120, "UNIDADE DE CONTROLE", ATIVO_ROXO,
            String.format("MPC: 0x%02X (%d)", processador.getMpc().getValor(), processador.getMpc().getValor()),
            String.format("ALU=%d | AMUX=%d | COND=%d", mir.getALU(), mir.getAMUX(), mir.getCOND()),
            String.format("RD=%d WR=%d MAR=%d MBR=%d ENC=%d", mir.getRD(), mir.getWR(), mir.getMAR(), mir.getMBR(), mir.getENC())
        );

        // 2. BANCO DE REGISTRADORES (Centro-Topo)
        int bx = cx - 150;
        desenharBloco(g2, bx, 20, 300, 125, "BANCO DE REGISTRADORES", encAtivo ? ATIVO_VERDE : BLOCO_BORDA,
            String.format("PC: 0x%04X   |   AC: 0x%04X (%d)", regs.get("PC"), regs.get("AC"), (short) regs.get("AC").intValue()),
            String.format("IR: 0x%04X   |   TIR: 0x%04X", regs.get("IR"), regs.get("TIR")),
            String.format("Bus A: reg[%d]   |   Bus B: reg[%d]   |   Bus C: reg[%d]", mir.getA(), mir.getB(), mir.getC())
        );

        // 3. AMUX (Esquerda-Meio)
        desenharBloco(g2, bx - 40, 175, 130, 52, "AMUX (" + (amuxUsaMBR ? "MBR" : "Bus A") + ")", ATIVO_AZUL,
            amuxUsaMBR ? "Entrada: MBR" : "Entrada: Latch A"
        );

        // 4. ULA (Centro-Meio)
        String opUla = switch (mir.getALU()) {
            case 0 -> "A + B (Soma)";
            case 1 -> "A AND B";
            case 2 -> "A OR B";
            case 3 -> "NOT A";
            default -> "PASSA A";
        };
        desenharBloco(g2, bx + 25, 250, 250, 68, "ULA — " + opUla, ATIVO_LARANJA,
            String.format("SH=%d | C=%s", mir.getSH(), encAtivo ? "Gravando reg[" + mir.getC() + "]" : "Inativo")
        );

        // 5. MAR & MBR (Direita)
        int mx = w - 270;
        desenharBloco(g2, mx, 30, 235, 75, "MAR (End. Memória)", marAtivo ? ATIVO_LARANJA : BLOCO_BORDA,
            String.format("Endereço: 0x%03X (%d)", processador.getMar().getEndereco(), processador.getMar().getEndereco())
        );

        desenharBloco(g2, mx, 130, 235, 75, "MBR (Dado Memória)", (mbrAtivo || rdAtivo) ? ATIVO_AZUL : BLOCO_BORDA,
            String.format("Dado: 0x%04X (%d)", processador.getMbr().getValor(), (short) processador.getMbr().getValor())
        );

        // 6. CACHE & MEMÓRIA PRINCIPAL (Direita-Inferior)
        Color corMem = rdAtivo ? ATIVO_AZUL : (wrAtivo ? ATIVO_LARANJA : BLOCO_BORDA);
        String statusMem = rdAtivo ? "LENDO (RD=1)" : (wrAtivo ? "ESCREVENDO (WR=1)" : "Ociosa");
        desenharBloco(g2, mx, 235, 235, 80, "CACHE / MEMÓRIA PRINCIPAL", corMem,
            "Status: " + statusMem,
            "Mapeamento Direto (8 linhas x 4)"
        );

        // ================= BARRAMENTOS ANIMADOS =================
        // Barramento A (Regs -> AMUX)
        desenharSeta(g2, bx + 40, 145, bx + 25, 175, !amuxUsaMBR ? ATIVO_AZUL : INATIVO_LINHA, "Bus A");
        // Barramento B (Regs -> ULA e MAR)
        desenharSeta(g2, bx + 230, 145, bx + 230, 250, ATIVO_AZUL, "Bus B");
        if (marAtivo) {
            desenharSeta(g2, bx + 300, 75, mx, 70, ATIVO_LARANJA, "MAR := B");
        }
        // AMUX -> ULA
        desenharSeta(g2, bx + 45, 227, bx + 85, 250, ATIVO_AZUL, "");
        // MBR -> AMUX
        desenharSeta(g2, mx, 175, bx + 90, 200, amuxUsaMBR ? ATIVO_AZUL : INATIVO_LINHA, "MBR");
        // ULA -> MBR (escrita STOD)
        if (mbrAtivo) {
            desenharSeta(g2, bx + 275, 280, mx, 180, ATIVO_LARANJA, "MBR := ULA");
        }
        // Barramento C (ULA -> Banco de Registradores)
        desenharSeta(g2, bx + 25, 285, bx - 10, 85, encAtivo ? ATIVO_VERDE : INATIVO_LINHA, "Bus C");
        // Barramento Memória (MAR/MBR <-> Cache/MP)
        desenharSeta(g2, mx + 115, 205, mx + 115, 235, (rdAtivo || wrAtivo) ? corMem : INATIVO_LINHA, rdAtivo ? "RD" : (wrAtivo ? "WR" : ""));
    }

    private void desenharBloco(Graphics2D g2, int x, int y, int w, int h, String titulo, Color borda, String... linhas) {
        g2.setColor(BLOCO_FUNDO);
        g2.fill(new RoundRectangle2D.Double(x, y, w, h, 12, 12));

        g2.setStroke(new BasicStroke(borda == BLOCO_BORDA ? 1.5f : 2.5f));
        g2.setColor(borda);
        g2.draw(new RoundRectangle2D.Double(x, y, w, h, 12, 12));

        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        g2.setColor(borda == BLOCO_BORDA ? new Color(180, 195, 220) : borda);
        g2.drawString(titulo, x + 12, y + 20);

        g2.setFont(new Font("Monospaced", Font.PLAIN, 12));
        g2.setColor(Color.WHITE);
        for (int i = 0; i < linhas.length; i++) {
            g2.drawString(linhas[i], x + 12, y + 40 + (i * 18));
        }
    }

    private void desenharSeta(Graphics2D g2, int x1, int y1, int x2, int y2, Color cor, String rotulo) {
        g2.setStroke(new BasicStroke(cor == INATIVO_LINHA ? 1.5f : 2.8f));
        g2.setColor(cor);
        g2.drawLine(x1, y1, x2, y2);
        g2.fillOval(x2 - 4, y2 - 4, 8, 8);
        if (!rotulo.isEmpty()) {
            g2.setFont(new Font("SansSerif", Font.BOLD, 11));
            g2.drawString(rotulo, (x1 + x2) / 2 + 4, (y1 + y2) / 2 - 4);
        }
    }
}