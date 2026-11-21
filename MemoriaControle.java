public class MemoriaControle {
    private int[] microinstrucoes;

    public MemoriaControle(int[] microprograma) {
        this.microinstrucoes = new int[256];
        if (microprograma != null) {
            int limite = Math.min(microprograma.length, 256);
            System.arraycopy(microprograma, 0, this.microinstrucoes, 0, limite);
        }
    }

    public int leitura(int endereco) {
        if (endereco < 0 || endereco >= microinstrucoes.length) {
            throw new IndexOutOfBoundsException("Endereço da memória de controle inválido: " + endereco);
        }
        return this.microinstrucoes[endereco];
    }

    public void escrita(int microinstrucao, int endereco) {
        if (endereco < 0 || endereco >= microinstrucoes.length) {
            throw new IndexOutOfBoundsException("Endereço da memória de controle inválido: " + endereco);
        }
        this.microinstrucoes[endereco] = microinstrucao;
    }

    public int getTamanho() {
        return microinstrucoes.length;
    }
}