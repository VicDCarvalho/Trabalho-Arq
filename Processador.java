import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class Processador {
    // ---- Índices dos registradores (iguais aos de BancoRegistradores) ----
    private static final int PC = 0, AC = 1, SP = 2, IR = 3, TIR = 4;
    private static final int ZERO = 5, UM = 6, MENOS_UM = 7, AMASK = 8, SMASK = 9;
    private static final int RA = 10; // registrador auxiliar "A" (usado como temporário)

    // Endereço do laço de HALT na memória de controle
    private static final int ENDERECO_HALT = 31;

    private UnidadeControle uc;
    private MemoriaControle memControle;
    private Memoria memoriaPrincipal;
    private Cache cache;
    private BancoRegistradores bancoRegs;
    private ULA ula;
    private Deslocador deslocador;
    private AMux amux;
    private Multiplexador mux;
    private Montador montador;

    private MIR mir;
    private MPC mpc;
    private MAR mar;
    private MBR mbr;
    private Latch latchA;
    private Latch latchB;

    // Microprograma em uso (firmware padrão ou customizado). Sobrevive a "gravar na MP" e "reset".
    private int[] microprogramaAtual;

    private int ciclosLeituraPendentes;
    private int ciclosEscritaPendentes;
    private int ciclosExecutados;
    private boolean finalizado;
    private String descricaoMicroinstrucaoAtual;

    public Processador() {
        this.microprogramaAtual = gerarFirmwarePadrao();
        inicializarComponentes(microprogramaAtual);
    }

    private void inicializarComponentes(int[] microprograma) {
        this.memoriaPrincipal = new Memoria(256);
        this.cache = new Cache(memoriaPrincipal, 8, 4);

        this.memControle = new MemoriaControle(microprograma);
        this.mir = new MIR();
        this.mpc = new MPC();
        this.uc = new UnidadeControle(mpc, mir, memControle);

        this.bancoRegs = new BancoRegistradores();
        this.ula = new ULA();
        this.deslocador = new Deslocador();
        this.amux = new AMux();
        this.mux = new Multiplexador();
        this.mar = new MAR();
        this.mbr = new MBR();
        this.latchA = new Latch(0);
        this.latchB = new Latch(0);
        this.montador = new Montador();

        this.ciclosLeituraPendentes = 0;
        this.ciclosEscritaPendentes = 0;
        this.ciclosExecutados = 0;
        this.finalizado = false;
        this.descricaoMicroinstrucaoAtual = "Aguardando início...";
    }
    private int[] gerarFirmwarePadrao() {
        int[] rom = new int[256];

        // Parâmetros de m(): amux, cond, alu, sh, mbr, mar, rd, wr, enc, c, b, a, addr

        // ================= BUSCA (FETCH) =================
        rom[0] = m(1,0,0,0,0,1,1,0,0, 0,  PC, 0,  1);          // MAR := PC; RD
        rom[1] = m(1,0,0,0,0,0,1,0,1, PC, UM, PC, 2);          // PC := PC + 1; RD
        rom[2] = m(0,1,0,0,0,0,0,0,1, IR, ZERO,0, 64);         // IR := MBR; se bit15=1 -> 64

        // ================= DECODIFICAÇÃO 0x0..0x7 =================
        rom[3] = m(1,1,0,0,0,0,0,0,1, TIR,IR, IR, 15);         // TIR := IR+IR; bit14=1 -> 15 (4..7)
        rom[4] = m(1,1,0,0,0,0,0,0,1, TIR,TIR,TIR, 8);         // bit13=1 -> 8 (2xxx/3xxx)
        rom[5] = m(1,1,0,0,0,0,0,0,1, TIR,TIR,TIR, 7);         // bit12=1 -> 7 (STOD) senão 6 (LODD)
        rom[8] = m(1,1,0,0,0,0,0,0,1, TIR,TIR,TIR, 11);        // bit12=1 -> 11 (SUBD) senão 9 (ADDD)
        rom[15]= m(1,1,0,0,0,0,0,0,1, TIR,TIR,TIR, 18);        // bit13=1 -> 18 (6xxx/7xxx)
        rom[16]= m(1,1,0,0,0,0,0,0,1, TIR,TIR,TIR, 27);        // bit12=1 -> 27 (JZER) senão 17 (JPOS)
        rom[18]= m(1,1,0,0,0,0,0,0,1, TIR,TIR,TIR, 28);        // bit12=1 -> 28 (LOCO) senão 19 (JUMP)

        // ================= LODD: AC := M[x] =================
        rom[6]  = m(1,3,1,0,0,0,0,0,1, RA, AMASK, IR, 20);     // RA := IR AND AMASK
        rom[20] = m(1,3,0,0,0,1,1,0,0, 0,  RA, 0, 21);         // MAR := RA; RD
        rom[21] = m(1,3,0,0,0,0,1,0,0, 0,  0,  0, 40);         // RD
        rom[40] = m(0,3,0,0,0,0,0,0,1, AC, ZERO,0, 0);         // AC := MBR

        // ================= STOD: M[x] := AC =================
        rom[7]  = m(1,3,1,0,0,0,0,0,1, RA, AMASK, IR, 22);     // RA := IR AND AMASK
        rom[22] = m(1,3,0,0,0,1,0,0,0, 0,  RA, 0, 12);         // MAR := RA
        rom[12] = m(1,3,0,0,1,0,0,1,0, 0,  ZERO, AC, 41);      // MBR := AC + 0; WR
        rom[41] = m(1,3,0,0,0,0,0,1,0, 0,  0,  0, 0);          // WR (2º ciclo)

        // ================= ADDD: AC := AC + M[x] =================
        rom[9]  = m(1,3,1,0,0,0,0,0,1, RA, AMASK, IR, 23);
        rom[23] = m(1,3,0,0,0,1,1,0,0, 0,  RA, 0, 24);         // MAR := RA; RD
        rom[24] = m(1,3,0,0,0,0,1,0,0, 0,  0,  0, 42);         // RD
        rom[42] = m(0,3,0,0,0,0,0,0,1, AC, AC, 0, 0);          // AC := MBR + AC

        // ================= SUBD: AC := AC - M[x] =================
        rom[11] = m(1,3,1,0,0,0,0,0,1, RA, AMASK, IR, 25);
        rom[25] = m(1,3,0,0,0,1,1,0,0, 0,  RA, 0, 26);         // MAR := RA; RD
        rom[26] = m(1,3,0,0,0,0,1,0,0, 0,  0,  0, 32);         // RD
        rom[32] = m(0,3,3,0,0,0,0,0,1, RA, 0,  0, 33);         // RA := NOT MBR   (AMUX=0 -> MBR)
        rom[33] = m(1,3,0,0,0,0,0,0,1, RA, UM, RA, 43);        // RA := RA + 1
        rom[43] = m(1,3,0,0,0,0,0,0,1, AC, AC, RA, 0);         // AC := RA + AC

        // ================= JPOS / JZER / JUMP / LOCO =================
        rom[17] = m(1,3,0,0,0,0,0,0,0, 0, 0, 0, 44);           // JPOS
        rom[44] = m(1,1,0,0,0,0,0,0,0, 0, ZERO, AC, 46);       // testa AC (AMUX=1); N=1 -> 46
        rom[45] = m(1,3,0,0,0,0,0,0,0, 0, 0, 0, 29);           // AC >= 0 -> PC := x
        rom[46] = m(1,3,0,0,0,0,0,0,0, 0, 0, 0, 0);            // AC < 0  -> próxima instrução

        rom[19] = m(1,3,0,0,0,0,0,0,0, 0, 0, 0, 29);           // JUMP -> PC := x

        rom[27] = m(1,3,0,0,0,0,0,0,0, 0, 0, 0, 38);           // JZER
        rom[38] = m(1,2,0,0,0,0,0,0,0, 0, ZERO, AC, 29);       // testa AC; Z=1 -> 29
        rom[39] = m(1,3,0,0,0,0,0,0,0, 0, 0, 0, 0);            // AC != 0 -> próxima instrução

        rom[28] = m(1,3,1,0,0,0,0,0,1, AC, AMASK, IR, 0);      // LOCO: AC := IR AND AMASK
        rom[29] = m(1,3,1,0,0,0,0,0,1, PC, AMASK, IR, 0);      // PC := IR AND AMASK

        // ================= DECODIFICAÇÃO 0x8..0xF (bit15 = 1) =================
        rom[64] = m(1,1,0,0,0,0,0,0,1, TIR,IR, IR, 80);        // TIR := IR+IR; bit14=1 -> 80 (C..F)
        rom[65] = m(1,1,0,0,0,0,0,0,1, TIR,TIR,TIR, 70);       // bit13=1 -> 70 (ADDL/SUBL)
        rom[66] = m(1,1,0,0,0,0,0,0,1, TIR,TIR,TIR, 68);       // bit12=1 -> 68 (STOL) senão 67 (LODL)

        // ---- LODL: AC := M[SP + x]  (reusa o final do LODD) ----
        rom[67]  = m(1,3,1,0,0,0,0,0,1, RA, AMASK, IR, 110);   // RA := IR AND AMASK
        rom[110] = m(1,3,0,0,0,0,0,0,1, RA, SP, RA, 20);       // RA := RA + SP
        // ---- STOL: M[SP + x] := AC  (reusa o final do STOD) ----
        rom[68]  = m(1,3,1,0,0,0,0,0,1, RA, AMASK, IR, 111);
        rom[111] = m(1,3,0,0,0,0,0,0,1, RA, SP, RA, 22);
        // ---- 70: ADDL (bit12=0) / SUBL (bit12=1) ----
        rom[70]  = m(1,1,0,0,0,0,0,0,1, TIR,TIR,TIR, 72);      // bit12=1 -> 72 (SUBL) senão 71 (ADDL)
        rom[71]  = m(1,3,1,0,0,0,0,0,1, RA, AMASK, IR, 112);   // ADDL
        rom[112] = m(1,3,0,0,0,0,0,0,1, RA, SP, RA, 23);       // -> final do ADDD
        rom[72]  = m(1,3,1,0,0,0,0,0,1, RA, AMASK, IR, 113);   // SUBL
        rom[113] = m(1,3,0,0,0,0,0,0,1, RA, SP, RA, 25);       // -> final do SUBD

        // ---- 0xC..0xF ----
        rom[80] = m(1,1,0,0,0,0,0,0,1, TIR,TIR,TIR, 90);       // bit13=1 -> 90 (E/F) senão 81 (C/D)
        rom[81] = m(1,1,0,0,0,0,0,0,1, TIR,TIR,TIR, 85);       // bit12=1 -> 85 (JNZE) senão 82 (JNEG)

        // JNEG (0xC): se AC < 0, PC := x
        rom[82] = m(1,1,0,0,0,0,0,0,0, 0, ZERO, AC, 29);       // N=1 -> 29
        rom[83] = m(1,3,0,0,0,0,0,0,0, 0, 0, 0, 0);            // senão -> próxima instrução
        // JNZE (0xD): se AC != 0, PC := x
        rom[85] = m(1,2,0,0,0,0,0,0,0, 0, ZERO, AC, 0);        // Z=1 -> 0 (não salta)
        rom[86] = m(1,3,0,0,0,0,0,0,0, 0, 0, 0, 29);           // senão -> 29

        rom[90] = m(1,1,0,0,0,0,0,0,1, TIR,TIR,TIR, 92);       // bit12=1 -> 92 (0xF) senão 91 (CALL)

        // CALL (0xE): SP := SP-1; M[SP] := PC; PC := x
        rom[91]  = m(1,3,0,0,0,0,0,0,1, SP, MENOS_UM, SP, 120);// SP := SP + (-1)
        rom[120] = m(1,3,0,0,0,1,0,0,0, 0, SP, 0, 121);        // MAR := SP
        rom[121] = m(1,3,0,0,1,0,0,1,0, 0, ZERO, PC, 122);     // MBR := PC; WR
        rom[122] = m(1,3,1,0,0,0,0,1,1, PC, AMASK, IR, 0);     // WR; PC := IR AND AMASK

        // ---- 0xF: instruções estendidas; decodifica bits 11,10,9 ----
        rom[92]  = m(1,1,0,0,0,0,0,0,1, TIR,TIR,TIR, 100);     // bit11=1 -> 100 (RETN/SWAP/INSP/DESP)
        rom[93]  = m(1,1,0,0,0,0,0,0,1, TIR,TIR,TIR, 97);      // bit10=1 -> 97 (PUSH/POP)
        rom[94]  = m(1,1,0,0,0,0,0,0,1, TIR,TIR,TIR, 96);      // bit9=1  -> 96 (POPI) senão 95 (PSHI)
        rom[97]  = m(1,1,0,0,0,0,0,0,1, TIR,TIR,TIR, 99);      // bit9=1  -> 99 (POP)  senão 98 (PUSH)
        rom[100] = m(1,1,0,0,0,0,0,0,1, TIR,TIR,TIR, 105);     // bit10=1 -> 105 (INSP/DESP)
        rom[101] = m(1,1,0,0,0,0,0,0,1, TIR,TIR,TIR, 103);     // bit9=1  -> 103 (SWAP) senão 102 (RETN)
        rom[105] = m(1,1,0,0,0,0,0,0,1, TIR,TIR,TIR, 107);     // bit9=1  -> 107 (DESP) senão 106 (INSP)

        // PSHI: SP := SP-1; M[SP] := M[AC]
        rom[95]  = m(1,3,0,0,0,0,0,0,1, SP, MENOS_UM, SP, 130);
        rom[130] = m(1,3,0,0,0,1,1,0,0, 0, AC, 0, 131);        // MAR := AC; RD
        rom[131] = m(1,3,0,0,0,0,1,0,0, 0, 0, 0, 132);         // RD  (MBR := M[AC])
        rom[132] = m(1,3,0,0,0,1,0,1,0, 0, SP, 0, 133);        // MAR := SP; WR
        rom[133] = m(1,3,0,0,0,0,0,1,0, 0, 0, 0, 0);           // WR

        // POPI: M[AC] := M[SP]; SP := SP+1
        rom[96]  = m(1,3,0,0,0,1,1,0,0, 0, SP, 0, 134);        // MAR := SP; RD
        rom[134] = m(1,3,0,0,0,0,1,0,0, 0, 0, 0, 135);         // RD  (MBR := M[SP])
        rom[135] = m(1,3,0,0,0,1,0,1,0, 0, AC, 0, 136);        // MAR := AC; WR
        rom[136] = m(1,3,0,0,0,0,0,1,1, SP, UM, SP, 0);        // WR; SP := SP + 1

        // PUSH: SP := SP-1; M[SP] := AC
        rom[98]  = m(1,3,0,0,0,0,0,0,1, SP, MENOS_UM, SP, 137);
        rom[137] = m(1,3,0,0,0,1,0,0,0, 0, SP, 0, 138);        // MAR := SP
        rom[138] = m(1,3,0,0,1,0,0,1,0, 0, ZERO, AC, 139);     // MBR := AC; WR
        rom[139] = m(1,3,0,0,0,0,0,1,0, 0, 0, 0, 0);           // WR

        // POP: AC := M[SP]; SP := SP+1
        rom[99]  = m(1,3,0,0,0,1,1,0,0, 0, SP, 0, 140);        // MAR := SP; RD
        rom[140] = m(1,3,0,0,0,0,1,0,1, SP, UM, SP, 141);      // RD; SP := SP + 1
        rom[141] = m(0,3,0,0,0,0,0,0,1, AC, ZERO, 0, 0);       // AC := MBR

        // RETN: PC := M[SP]; SP := SP+1
        rom[102] = m(1,3,0,0,0,1,1,0,0, 0, SP, 0, 142);       // MAR := SP; RD
        rom[142] = m(1,3,0,0,0,0,1,0,1, SP, UM, SP, 143);     // RD; SP := SP + 1
        rom[143] = m(0,3,0,0,0,0,0,0,1, PC, ZERO, 0, 0);      // PC := MBR

        // SWAP: AC <-> SP
        rom[103] = m(1,3,0,0,0,0,0,0,1, RA, ZERO, AC, 144);   // RA := AC
        rom[144] = m(1,3,0,0,0,0,0,0,1, AC, ZERO, SP, 145);   // AC := SP
        rom[145] = m(1,3,0,0,0,0,0,0,1, SP, ZERO, RA, 0);     // SP := RA

        // INSP y: SP := SP + y
        rom[106] = m(1,3,1,0,0,0,0,0,1, RA, SMASK, IR, 146);  // RA := IR AND SMASK
        rom[146] = m(1,3,0,0,0,0,0,0,1, SP, SP, RA, 0);       // SP := RA + SP

        // DESP y: SP := SP - y   (0xFFFF = HALT: é detectado aqui, pois 0xFFFF + 1 = 0)
        rom[107] = m(1,2,0,0,0,0,0,0,1, RA, UM, IR, ENDERECO_HALT); // RA := IR+1; Z=1 -> HALT
        rom[108] = m(1,3,1,0,0,0,0,0,1, RA, SMASK, IR, 147);  // RA := IR AND SMASK
        rom[147] = m(1,3,3,0,0,0,0,0,1, RA, 0, RA, 148);      // RA := NOT RA
        rom[148] = m(1,3,0,0,0,0,0,0,1, RA, UM, RA, 149);     // RA := RA + 1
        rom[149] = m(1,3,0,0,0,0,0,0,1, SP, SP, RA, 0);       // SP := RA + SP

        // ================= HALT =================
        rom[ENDERECO_HALT] = microHalt();

        return rom;
    }

    private int microHalt() {
        return m(1,3,0,0,0,0,0,0,0, 0, 0, 0, ENDERECO_HALT);
    }

    // Atalho curto para montar microinstruções nas tabelas acima
    private int m(int amux, int cond, int alu, int sh, int mbr, int mar,
                  int rd, int wr, int enc, int c, int b, int a, int addr) {
        return codificarMicro(amux, cond, alu, sh, mbr, mar, rd, wr, enc, c, b, a, addr);
    }

    private int codificarMicro(int amux, int cond, int alu, int sh, int mbr, int mar,
                               int rd, int wr, int enc, int c, int b, int a, int addr) {
        int m = 0;
        m = Campo.AMUX.inserir(m, amux);
        m = Campo.COND.inserir(m, cond);
        m = Campo.ALU.inserir(m, alu);
        m = Campo.SH.inserir(m, sh);
        m = Campo.MBR.inserir(m, mbr);
        m = Campo.MAR.inserir(m, mar);
        m = Campo.RD.inserir(m, rd);
        m = Campo.WR.inserir(m, wr);
        m = Campo.ENC.inserir(m, enc);
        m = Campo.C.inserir(m, c);
        m = Campo.B.inserir(m, b);
        m = Campo.A.inserir(m, a);
        m = Campo.ADDR.inserir(m, addr);
        return m;
    }

    /** Grava o programa (macroinstruções) na MP e reinicia o processador, mantendo o microprograma atual. */
    public void carregarProgramaNaMP(int[] palavrasPrograma) {
        inicializarComponentes(microprogramaAtual);
        for (int i = 0; i < Math.min(palavrasPrograma.length, memoriaPrincipal.getTamanho()); i++) {
            memoriaPrincipal.escrita(palavrasPrograma[i] & 0xFFFF, i);
        }
    }

    public void carregarMicroprograma(String codigoMicroinstrucoes) throws IOException {
        Path tempFile = Files.createTempFile("microprograma_", ".txt");
        try {
            Files.writeString(tempFile, codigoMicroinstrucoes);
            this.montador = new Montador();
            this.microprogramaAtual = montador.montar(tempFile.toString());
        } finally {
            Files.deleteIfExists(tempFile);
        }
        inicializarComponentes(microprogramaAtual);
    }

    /** Volta ao firmware padrão (descarta um microprograma customizado). */
    public void restaurarFirmwarePadrao() {
        this.microprogramaAtual = gerarFirmwarePadrao();
        inicializarComponentes(microprogramaAtual);
    }

    public String executarCiclo() {
        if (finalizado) return "Execução finalizada (HALT).\n";

        int mpcAtual = mpc.getValor();
        if (mpcAtual == ENDERECO_HALT && memControle.leitura(ENDERECO_HALT) == microHalt()) {
            finalizado = true;
            descricaoMicroinstrucaoAtual = "HALT (Programa finalizado)";
            return "HALT alcançado. Execução concluída.\n";
        }

        try {
            // SUBCICLO 1: Busca microinstrução
            uc.buscarMicroinstrucao();
            int microinstrucao = memControle.leitura(mpcAtual);

            // SUBCICLO 2: Leitura dos registradores A e B
            int valorA = bancoRegs.ler(mir.getA());
            int valorB = bancoRegs.ler(mir.getB());
            latchA.habilitaEntrada(valorA);
            latchB.habilitaEntrada(valorB);

            // SUBCICLO 3: AMux -> ULA -> Deslocador -> MAR
            int entradaAUla = amux.saida(latchA.habilitaSaida(), mbr.getValor(), mir.getAMUX());
            int entradaBUla = latchB.habilitaSaida();
            int resultadoUla = ula.computar(entradaAUla, entradaBUla, mir.getALU());
            int saidaDeslocador = deslocador.computar(resultadoUla, mir.getSH());

            if (mir.getMAR() == 1) {
                mar.carregar(entradaBUla);
            }

            // SUBCICLO 4: Escrita no C, MBR, Cache/MP e MPC
            if (mir.getENC() == 1) {
                bancoRegs.escrever(mir.getC(), saidaDeslocador, 1);
            }
            if (mir.getMBR() == 1) {
                mbr.carregar(saidaDeslocador);
            }

            processarMemoria(mir.getRD(), mir.getWR());
            uc.atualizarMPC(ula.getFlagN(), ula.getFlagZ(), mux);

            ciclosExecutados++;
            descricaoMicroinstrucaoAtual = formatarDescricaoMicro(mpcAtual, microinstrucao);
            return descricaoMicroinstrucaoAtual + "\n";
        } catch (RuntimeException e) {
            // Ex.: acesso a endereço fora da MP (SP não inicializado, endereço > 255...)
            finalizado = true;
            descricaoMicroinstrucaoAtual = "ERRO no MPC=0x" + Integer.toHexString(mpcAtual).toUpperCase()
                    + ": " + e.getMessage();
            return descricaoMicroinstrucaoAtual + "\n";
        }
    }

    private String formatarDescricaoMicro(int mpcOrigem, int micro) {
        String bin = String.format("%32s", Integer.toBinaryString(micro)).replace(' ', '0');
        return String.format(
            "MPC[0x%02X -> 0x%02X] | MIR: %s | AMUX=%d COND=%d ALU=%d SH=%d MBR=%d MAR=%d RD=%d WR=%d ENC=%d C=%d B=%d A=%d ADDR=0x%02X",
            mpcOrigem, mpc.getValor(), bin,
            mir.getAMUX(), mir.getCOND(), mir.getALU(), mir.getSH(),
            mir.getMBR(), mir.getMAR(), mir.getRD(), mir.getWR(),
            mir.getENC(), mir.getC(), mir.getB(), mir.getA(), mir.getADDR()
        );
    }

    private void processarMemoria(int rd, int wr) {
        if (rd == 1) {
            ciclosLeituraPendentes++;
            if (ciclosLeituraPendentes >= 2) {
                int dadoLido = cache.leitura(mar.getEndereco());
                mbr.carregar(dadoLido);
                ciclosLeituraPendentes = 0;
            }
        } else {
            ciclosLeituraPendentes = 0;
        }

        if (wr == 1) {
            ciclosEscritaPendentes++;
            if (ciclosEscritaPendentes >= 2) {
                cache.escrita(mbr.getValor(), mar.getEndereco());
                cache.flush(); // Sincroniza com a MP para exibição em tempo real na interface
                ciclosEscritaPendentes = 0;
            }
        } else {
            ciclosEscritaPendentes = 0;
        }
    }

    
    public void resetar() {
        inicializarComponentes(microprogramaAtual);
    }

    public Map<String, Integer> getMapaRegistradores() {
        String[] nomes = {
            "PC", "AC", "SP", "IR", "TIR",
            "0", "+1", "-1", "AMASK", "SMASK",
            "A", "B", "C", "D", "E", "F"
        };
        Map<String, Integer> mapa = new LinkedHashMap<>();
        for (int i = 0; i < 16; i++) {
            mapa.put(nomes[i], bancoRegs.ler(i));
        }
        return mapa;
    }

    public String getDescricaoMicroinstrucaoAtual() { return descricaoMicroinstrucaoAtual; }
    public boolean isFinalizado() { return finalizado; }
    public int getCiclosExecutados() { return ciclosExecutados; }
    public Memoria getMemoriaPrincipal() { return memoriaPrincipal; }
    public Cache getCache() { return cache; }
    public MemoriaControle getMemoriaControle() { return memControle; }
    public MPC getMpc() { return mpc; }
    public MIR getMir() { return mir; }
    public MAR getMar() { return mar; }
    public MBR getMbr() { return mbr; }
}