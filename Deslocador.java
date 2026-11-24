public class Deslocador {
    public int computar(int entrada, int controle){
        int v = entrada & 0xFFFF;
        int resultado;

        switch(controle){
            case 0: //sem deslocamento
                resultado = v;
                break;
            case 1: //SRA1
            
                resultado = (v >> 1) | (v & 0x8000);
                break;       
            case 2: //SLL8
                resultado = v << 8;
                break;
            default:
                throw new IllegalArgumentException("Controle inválido");  
        }

        return resultado & 0xFFFF;
    }
}
