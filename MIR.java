public class MIR {
    private int valor;

    public void carregar(int microInst) {
        this.valor = microInst;
    }

    public int getAMUX() {
        return (valor >>> 31) & 0b1;
    }
    
    public int getCOND() {
        return (valor >>> 29) & 0b11; 
    }

    public int getALU() {
        return (valor >>> 27) & 0b11;
    }

    public int getSH() {
        return (valor >>> 25) & 0b11;
    }
    
    public int getMBR() {
        return (valor >>> 24) & 0b1;
    }

    public int getMAR() {
        return (valor >>> 23) & 0b1;
    }

    public int getRD() {
        return (valor >>> 22) & 0b1;
    }
    
    public int getWR() {
        return (valor >>> 21) & 0b1;
    }
    
    public int getENC() {
        return (valor >>> 20) & 0b1;
    }
    
    public int getC() {
        return (valor >>> 16) & 0xF;
    }
    
    public int getB() {
        return (valor >>> 12) & 0xF;
    }

    public int getA() {
        return (valor >>> 8) & 0xF;
    }

    public int getADDR() {
        return valor & 0xFF;
    }
}
