public class Registrador {
    private int valor;

    public void carregar(int entrada) {
        this.valor = entrada & 0xFFFF;
    }

    public int saida() {
        return this.valor & 0xFFFF;
    }
}