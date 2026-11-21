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
     * Gera o microprograma padrão (firmware) do MIC-1 para interpretar
     * o conjunto de instruções MAC-1 gravado na Memória Principal.
     */
    private int[] gerarFirmwarePadrao() {
        int[] rom = new int[256];
        // 0: MAR := PC; RD (Busca instrução)
        rom[0] = codificarMicro(1, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 1);
        // 1: PC := PC + 1; RD
        rom[1] = codificarMicro(1, 0, 0, 0, 0, 0, 1, 0, 1, 0, 6, 0, 2);
        // 2: IR := MBR; se IR < 0 vai para instruções 0x8000..0xFFFF (endereço 30)
        rom[2] = codificarMicro(0, 1, 0, 0, 0, 0, 0, 0, 1, 3, 5, 0, 30);

        // --- Decodificação de Opcodes Positivos (0xxx a 7xxx) via deslocamento ---
        // 3: TIR := IR + IR (desloca bit 14 para sinal)
        rom[3] = codificarMicro(1, 1, 0, 0, 0, 0, 0, 0, 1, 4, 3, 3, 15);
        // 4: TIR := TIR + TIR (bit 13) -> se N=1 é 0010 (ADDD=8) ou 0011 (SUBD=11)
        rom[4] = codificarMicro(1, 1, 0, 0, 0, 0, 0, 0, 1, 4, 4, 4, 8);
        // 5: TIR := TIR + TIR (bit 12) -> se N=1 é 0001 (STOD=7), senão 0000 (LODD=6)
        rom[5] = codificarMicro(1, 1, 0, 0, 0, 0, 0, 0, 1, 4, 4, 4, 7);

        // 6: LODD (0xxx): MAR := IR; RD
        rom[6] = codificarMicro(1, 3, 0, 0, 0, 1, 1, 0, 0, 0, 3, 0, 20);
        // 7: STOD (1xxx): MAR := IR; MBR := AC; WR
        rom[7] = codificarMicro(1, 3, 0, 0, 1, 1, 0, 1, 0, 0, 3, 1, 22);

        // 8: Verifica ADDD (2xxx) vs SUBD (3xxx)
        rom[8] = codificarMicro(1, 1, 0, 0, 0, 0, 0, 0, 1, 4, 4, 4, 11);
        // 9: ADDD (2xxx): MAR := IR; RD
        rom[9] = codificarMicro(1, 3, 0, 0, 0, 1, 1, 0, 0, 0, 3, 0, 23);
        // 11: SUBD (3xxx): MAR := IR; RD
        rom[11] = codificarMicro(1, 3, 0, 0, 0, 1, 1, 0, 0, 0, 3, 0, 25);

        // 15: Verifica JPOS/JZER (4xxx/5xxx) vs JUMP/LOCO (6xxx/7xxx)
        rom[15] = codificarMicro(1, 1, 0, 0, 0, 0, 0, 0, 1, 4, 4, 4, 18);
        // 16: JPOS/JZER -> verifica bit 12
        rom[16] = codificarMicro(1, 1, 0, 0, 0, 0, 0, 0, 1, 4, 4, 4, 27);
        // 17: JPOS: testa AC (A := AC), se N=1 não salta, senão PC := IR
        rom[17] = codificarMicro(1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 5, 1, 0);
        rom[17] = codificarMicro(1, 3, 0, 0, 0, 0, 0, 0, 1, 0, 3, 5, 0); // PC := IR & AMASK (simplificado: IR)

        // 18: JUMP (6xxx) vs LOCO (7xxx)
        rom[18] = codificarMicro(1, 1, 0, 0, 0, 0, 0, 0, 1, 4, 4, 4, 19);
        // 18 (false -> JUMP 6xxx): PC := IR AND AMASK
        rom[18] = codificarMicro(1, 1, 0, 0, 0, 0, 0, 0, 1, 4, 4, 4, 28);
        // JUMP (29): PC := IR AND AMASK; volta para 0
        rom[29] = codificarMicro(1, 3, 1, 0, 0, 0, 0, 0, 1, 0, 8, 3, 0);
        // LOCO (28): AC := IR AND AMASK; volta para 0
        rom[28] = codificarMicro(1, 3, 1, 0, 0, 0, 0, 0, 1, 1, 8, 3, 0);

        // 20-21: Finaliza LODD -> RD; AC := MBR
        rom[20] = codificarMicro(1, 3, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 21);
        rom[21] = codificarMicro(0, 3, 0, 0, 0, 0, 0, 0, 1, 1, 5, 0, 0);

        // 22: Finaliza STOD -> WR; volta para 0
        rom[22] = codificarMicro(1, 3, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0);

        // 23-24: Finaliza ADDD -> RD; AC := MBR + AC
        rom[23] = codificarMicro(1, 3, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 24);
        rom[24] = codificarMicro(0, 3, 0, 0, 0, 0, 0, 0, 1, 1, 1, 0, 0);

        // 25-26: Finaliza SUBD -> RD; A := NOT MBR, depois AC := AC + A + 1
        rom[25] = codificarMicro(1, 3, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 26);
        rom[26] = codificarMicro(0, 3, 3, 0, 0, 0, 0, 0, 1, 10, 0, 0, 32);
        rom[32] = codificarMicro(1, 3, 0, 0, 0, 0, 0, 0, 1, 10, 6, 10, 33);
        rom[33] = codificarMicro(1, 3, 0, 0, 0, 0, 0, 0, 1, 1, 1, 10, 0);

        // 27: JZER (5xxx): testa AC, se Z=1 salta para 29 (PC := IR)
        rom[27] = codificarMicro(1, 2, 0, 0, 0, 0, 0, 0, 0, 0, 5, 1, 29);
        // Se não saltou, volta para 0
        rom[27] = codificarMicro(1, 2, 0, 0, 0, 0, 0, 0, 0, 0, 5, 1, 29);

        // 30: Opcodes negativos (JNEG 0xCxxx ou HALT 0xFFFF)
        // Se IR == -1 (0xFFFF = HALT), trava em 31; senão verifica JNEG (0xCxxx)
        rom[30] = codificarMicro(1, 3, 0, 0, 0, 0, 0, 0, 1, 10, 7, 3, 34); // A := IR + (-1) -> se IR=0xFFFF, A=0xFFFE
        rom[34] = codificarMicro(1, 3, 0, 0, 0, 0, 0, 0, 1, 10, 6, 3, 35); // A := IR + 1 -> se IR=0xFFFF, A=0 (Z=1!)
        rom[35] = codificarMicro(1, 2, 0, 0, 0, 0, 0, 0, 0, 0, 5, 10, 31); // Se Z=1 (HALT), vai para 31
        // 36: JNEG (0xCxxx): testa AC, se N=1 vai para 29 (PC := IR), senão volta para 0
        rom[36] = codificarMicro(1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 5, 1, 29);
        rom[37] = codificarMicro(1, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        // 31: Loop de HALT (Fim do programa)
        rom[31] = codificarMicro(1, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 31);

        // Ajuste de salto sequencial pós-JZER (27 -> 37 -> 0) e pós-35 (35 -> 36 -> 37 -> 0)
        // Nota: no MIC-1 real COND=2 salta se Z=1 e avança para MPC+1 se Z=0:
        // Como 27+1=28 (LOCO), redirecionamos 27 para 38:
        rom[27] = codificarMicro(1, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 38);
        rom[38] = codificarMicro(1, 2, 0, 0, 0, 0, 0, 0, 0, 0, 5, 1, 29);
        rom[39] = codificarMicro(1, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

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