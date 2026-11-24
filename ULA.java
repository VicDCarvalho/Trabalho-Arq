public class ULA{
    private boolean flagN; //Negativo
    private boolean flagZ; //Zero

    public int computar(int entradaA, int entradaB, int controle){
        int resultado = 0;
        int a = entradaA & 0xFFFF; 
        int b = entradaB & 0xFFFF;

        switch(controle){
            case 0: //A + B
                resultado = a + b;
                break;
            case 1: //A AND B
                resultado = a & b;
                break;
            case 2: //A OR b
                resultado = a | b;
                break;
            case 3: //Not a
                resultado = ~a;
                break;
            case 4: //Passa A direto
                resultado = a;
                break;
            default:
                throw new IllegalArgumentException("Sinal de controle inválido");
        }

        //Restringe o valor do resultado a 16 bits
        resultado = resultado & 0xFFFF;

        atualizarFlags(resultado);

        return resultado;
    }

    private void atualizarFlags(int resultado){
        this.flagZ = (resultado == 0);                  
        
        this.flagN = (resultado & 0x8000) != 0;         
                                                        
    }

    public boolean getFlagN() { return flagN; }
    public boolean getFlagZ() { return flagZ; }
}   

