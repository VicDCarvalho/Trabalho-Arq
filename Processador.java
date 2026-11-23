import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class Processador {
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

    private int ciclosLeituraPendentes;
    private int ciclosEscritaPendentes;
    private int ciclosExecutados;
    private boolean finalizado;
    private String descricaoMicroinstrucaoAtual;

    public Processador() {
        inicializarComponentes(gerarFirmwarePadrao());
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

    /**
     * Gera o microprograma padrão (firmware) corrigido do MIC-1 para interpretar
     * o conjunto de instruções MAC-1 gravado na Memória Principal.
     */
    private int[] gerarFirmwarePadrao() {
        int[] rom = new int[256];

        // --- CICLO DE BUSCA (FETCH) ---
        // 0: MAR := PC; RD
        rom[0] = codificarMicro(1, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 1);
        // 1: PC := PC + 1; RD (completa os 2 ciclos de leitura da Cache)
        rom[1] = codificarMicro(1, 0, 0, 0, 0, 0, 1, 0, 1, 0, 6, 0, 2);
        // 2: IR := MBR; se bit 15 == 1 (negativo) salta para 30 (JNEG / HALT)
        rom[2] = codificarMicro(0, 1, 0, 0, 0, 0, 0, 0, 1, 3, 5, 0, 30);

        // --- DECODIFICAÇÃO (Opcodes 0x0 a 0x7) ---
        // 3: TIR := IR + IR (desloca bit 14 para sinal -> testa se >= 4xxx)
        rom[3] = codificarMicro(1, 1, 0, 0, 0, 0, 0, 0, 1, 4, 3, 3, 15);
        // 4: TIR := TIR + TIR (bit 13 -> se N=1 é 2xxx ADDD ou 3xxx SUBD, vai para 8)
        rom[4] = codificarMicro(1, 1, 0, 0, 0, 0, 0, 0, 1, 4, 4, 4, 8);
        // 5: TIR := TIR + TIR (bit 12 -> se N=1 é 1xxx STOD [7], senão 0xxx LODD [6])
        rom[5] = codificarMicro(1, 1, 0, 0, 0, 0, 0, 0, 1, 4, 4, 4, 7);

        // --- LODD (0xxx): AC := MP[IR & AMASK] ---
        // 6: RegA(10) := IR AND AMASK (8)
        rom[6]  = codificarMicro(1, 3, 1, 0, 0, 0, 0, 0, 1, 10, 8, 3, 20);
        // 20: MAR := RegA(10); RD
        rom[20] = codificarMicro(1, 3, 0, 0, 0, 1, 1, 0, 0, 0, 10, 0, 21);
        // 21: RD (2º ciclo de leitura da Cache)
        rom[21] = codificarMicro(1, 3, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 40);
        // 40: AC := MBR; volta para 0
        rom[40] = codificarMicro(0, 3, 0, 0, 0, 0, 0, 0, 1, 1, 5, 0, 0);

        // --- STOD (1xxx): MP[IR & AMASK] := AC ---
        // 7: RegA(10) := IR AND AMASK (8)
        rom[7]  = codificarMicro(1, 3, 1, 0, 0, 0, 0, 0, 1, 10, 8, 3, 22);
        // 22: MAR := RegA(10); MBR := AC; WR
        rom[22] = codificarMicro(1, 3, 0, 0, 1, 1, 0, 1, 0, 0, 10, 1, 41);
        // 41: WR (2º ciclo de escrita na Cache); volta para 0
        rom[41] = codificarMicro(1, 3, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0);

        // --- 8: ADDD (2xxx) vs SUBD (3xxx) ---
        // bit 12 -> se N=1 é 3xxx SUBD [11], senão 2xxx ADDD [9]
        rom[8]  = codificarMicro(1, 1, 0, 0, 0, 0, 0, 0, 1, 4, 4, 4, 11);

        // --- ADDD (2xxx): AC := AC + MP[IR & AMASK] ---
        // 9: RegA(10) := IR AND AMASK (8)
        rom[9]  = codificarMicro(1, 3, 1, 0, 0, 0, 0, 0, 1, 10, 8, 3, 23);
        // 23: MAR := RegA(10); RD
        rom[23] = codificarMicro(1, 3, 0, 0, 0, 1, 1, 0, 0, 0, 10, 0, 24);
        // 24: RD (2º ciclo)
        rom[24] = codificarMicro(1, 3, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 42);
        // 42: AC := MBR + AC; volta para 0
        rom[42] = codificarMicro(0, 3, 0, 0, 0, 0, 0, 0, 1, 1, 1, 0, 0);

        // --- SUBD (3xxx): AC := AC - MP[IR & AMASK] ---
        // 11: RegA(10) := IR AND AMASK (8)
        rom[11] = codificarMicro(1, 3, 1, 0, 0, 0, 0, 0, 1, 10, 8, 3, 25);
        // 25: MAR := RegA(10); RD
        rom[25] = codificarMicro(1, 3, 0, 0, 0, 1, 1, 0, 0, 0, 10, 0, 26);
        // 26: RD (2º ciclo)
        rom[26] = codificarMicro(1, 3, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 32);
        // 32: RegA(10) := NOT MBR
        rom[32] = codificarMicro(0, 3, 3, 0, 0, 0, 0, 0, 1, 10, 0, 0, 33);
        // 33: RegA(10) := RegA(10) + 1 (complemento de 2)
        rom[33] = codificarMicro(1, 3, 0, 0, 0, 0, 0, 0, 1, 10, 6, 10, 43);
        // 43: AC := RegA(10) + AC; volta para 0
        rom[43] = codificarMicro(1, 3, 0, 0, 0, 0, 0, 0, 1, 1, 1, 10, 0);

        // --- SALTOS E CONSTANTE (4xxx a 7xxx) ---
        // 15: TIR := TIR + TIR (bit 13 -> se N=1 é 6xxx JUMP ou 7xxx LOCO [18])
        rom[15] = codificarMicro(1, 1, 0, 0, 0, 0, 0, 0, 1, 4, 4, 4, 18);
        // 16: TIR := TIR + TIR (bit 12 -> se N=1 é 5xxx JZER [27], senão 4xxx JPOS [17])
        rom[16] = codificarMicro(1, 1, 0, 0, 0, 0, 0, 0, 1, 4, 4, 4, 27);

        // 17: JPOS (4xxx) - redirecionado para área isolada (44..46)
        rom[17] = codificarMicro(1, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 44);
        // 44: Testa AC; se N=1 (negativo) salta para 46 (não salta), senão avança para 45
        rom[44] = codificarMicro(1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 5, 1, 46);
        // 45: AC >= 0 -> vai para 29 (PC := IR & AMASK)
        rom[45] = codificarMicro(1, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 29);
        // 46: AC < 0 -> volta para 0
        rom[46] = codificarMicro(1, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        // 18: JUMP (6xxx) vs LOCO (7xxx)
        rom[18] = codificarMicro(1, 1, 0, 0, 0, 0, 0, 0, 1, 4, 4, 4, 28); // bit 12 == 1 -> 28 (LOCO), senão 19
        rom[19] = codificarMicro(1, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 29); // JUMP -> 29

        // 27: JZER (5xxx) - redirecionado para área isolada (38..39)
        rom[27] = codificarMicro(1, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 38);
        // 38: Testa AC; se Z=1 salta para 29 (PC := IR & AMASK), senão avança para 39
        rom[38] = codificarMicro(1, 2, 0, 0, 0, 0, 0, 0, 0, 0, 5, 1, 29);
        // 39: AC != 0 -> volta para 0
        rom[39] = codificarMicro(1, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        // 28: LOCO (7xxx): AC := IR AND AMASK; volta para 0
        rom[28] = codificarMicro(1, 3, 1, 0, 0, 0, 0, 0, 1, 1, 8, 3, 0);
        // 29: JUMP (6xxx): PC := IR AND AMASK; volta para 0
        rom[29] = codificarMicro(1, 3, 1, 0, 0, 0, 0, 0, 1, 0, 8, 3, 0);

        // --- OPCODES NEGATIVOS (JNEG 0xCxxx e HALT 0xFFFF) ---
        // 30: RegA(10) := IR + 1 (se IR == 0xFFFF [HALT], resultado = 0 e Z=1)
        rom[30] = codificarMicro(1, 3, 0, 0, 0, 0, 0, 0, 1, 10, 6, 3, 35);
        // 35: Testa RegA(10); se Z=1 vai para 31 (HALT), senão avança para 36 (JNEG)
        rom[35] = codificarMicro(1, 2, 0, 0, 0, 0, 0, 0, 0, 0, 5, 10, 31);
        // 36: JNEG (0xCxxx): testa AC; se N=1 vai para 29 (salta), senão avança para 37 -> 0
        rom[36] = codificarMicro(1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 5, 1, 29);
        rom[37] = codificarMicro(1, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        // 31: Loop de HALT (Fim do programa)
        rom[31] = codificarMicro(1, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 31);

        return rom;
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

    public void carregarProgramaNaMP(int[] palavrasPrograma) {
        inicializarComponentes(gerarFirmwarePadrao());
        for (int i = 0; i < Math.min(palavrasPrograma.length, memoriaPrincipal.getTamanho()); i++) {
            memoriaPrincipal.escrita(palavrasPrograma[i] & 0xFFFF, i);
        }
    }

    public void carregarMicroprograma(String codigoMicroinstrucoes) throws IOException {
        Path tempFile = Files.createTempFile("microprograma_", ".txt");
        Files.writeString(tempFile, codigoMicroinstrucoes);
        this.montador = new Montador();
        int[] microprogramaMontado = montador.montar(tempFile.toString());
        Files.deleteIfExists(tempFile);
        inicializarComponentes(microprogramaMontado);
    }

    public String executarCiclo() {
        if (finalizado) return "Execução finalizada (HALT).\n";

        int mpcAtual = mpc.getValor();
        if (mpcAtual == 31 && memControle.leitura(31) == codificarMicro(1, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 31)) {
            finalizado = true;
            descricaoMicroinstrucaoAtual = "HALT (Programa finalizado)";
            return "HALT alcançado. Execução concluída.\n";
        }

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
        inicializarComponentes(gerarFirmwarePadrao());
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
```[cite: 17]