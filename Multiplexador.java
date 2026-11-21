public class Multiplexador {

    public int saida(int entrada0, int entrada1, int sinalControle) {
        if (sinalControle == 1) {
            return entrada1;
        } else {
            return entrada0;
        }
    }
}