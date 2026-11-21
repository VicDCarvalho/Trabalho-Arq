public class UnidadeControle {

    private MPC mpc;
    private MIR mir;
    private MemoriaControle memoriaControle;

    public UnidadeControle(MPC mpc, MIR mir, MemoriaControle memoriaControle) {
        this.mpc = mpc;
        this.mir = mir;
        this.memoriaControle = memoriaControle;
    }

    // Subciclo 1: Busca a microinstrução na memória de controle e carrega no MIR
    public void buscarMicroinstrucao() {
        int endereco = mpc.getValor();
        int microinstrucao = memoriaControle.leitura(endereco);
        mir.carregar(microinstrucao);
    }

    // Calcula e atualiza o próximo valor do MPC conforme COND e flags da ULA
    public void atualizarMPC(boolean flagN, boolean flagZ, Multiplexador mux) {
        int cond = mir.getCOND();
        int addr = mir.getADDR();
        int proximoSequencial = mpc.incrementar();

        int saltar = 0;
        switch (cond) {
            case 0: // Nunca salta (avança sequencialmente)
                saltar = 0;
                break;
            case 1: // JMPN: salta se N == 1
                saltar = flagN ? 1 : 0;
                break;
            case 2: // JMPZ: salta se Z == 1
                saltar = flagZ ? 1 : 0;
                break;
            case 3: // JMP: salta incondicionalmente
                saltar = 1;
                break;
        }

        int proximoEndereco = mux.saida(proximoSequencial, addr, saltar);
        mpc.carregar(proximoEndereco);
    }
}
