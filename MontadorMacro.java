import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MontadorMacro {
    private Map<String, Integer> labels = new LinkedHashMap<>();

    public static class ResultadoMontagem {
        public final int[] palavrasMemoria;
        public final List<String> linhasBinarias;

        public ResultadoMontagem(int[] palavrasMemoria, List<String> linhasBinarias) {
            this.palavrasMemoria = palavrasMemoria;
            this.linhasBinarias = linhasBinarias;
        }
    }

    public ResultadoMontagem montar(String codigoAssembly) {
        labels.clear();
        List<String> linhasLimpas = new ArrayList<>();

        for (String linha : codigoAssembly.split("\\R")) {
            int idxComent = linha.indexOf("//");
            if (idxComent != -1) linha = linha.substring(0, idxComent);
            idxComent = linha.indexOf(";");
            if (idxComent != -1) linha = linha.substring(0, idxComent);
            linha = linha.trim();
            if (!linha.isEmpty()) linhasLimpas.add(linha);
        }

        // 1ª Passada: mapeia labels
        int endereco = 0;
        for (String linha : linhasLimpas) {
            if (linha.contains(":")) {
                int idx = linha.indexOf(":");
                String label = linha.substring(0, idx).trim().toUpperCase();
                if (!label.isEmpty()) labels.put(label, endereco);
                linha = linha.substring(idx + 1).trim();
            }
            if (!linha.isEmpty()) endereco++;
        }

        // 2ª Passada: gera código de máquina de 16 bits
        int[] memoria = new int[256];
        List<String> binarios = new ArrayList<>();
        endereco = 0;

        for (int i = 0; i < linhasLimpas.size(); i++) {
            String linha = linhasLimpas.get(i);
            if (linha.contains(":")) {
                linha = linha.substring(linha.indexOf(":") + 1).trim();
                if (linha.isEmpty()) continue;
            }

            int palavra = codificarInstrucao(linha, i + 1);
            memoria[endereco] = palavra & 0xFFFF;
            String bin16 = String.format("%16s", Integer.toBinaryString(palavra & 0xFFFF)).replace(' ', '0');
            binarios.add(String.format("[%02d] %s  (0x%04X)", endereco, bin16, palavra & 0xFFFF));
            endereco++;
        }

        return new ResultadoMontagem(memoria, binarios);
    }

    private int codificarInstrucao(String linha, int numLinha) {
        String[] partes = linha.split("\\s+");
        String mnemonico = partes[0].toUpperCase();

        // Instruções sem operando (16 bits fixos)
        switch (mnemonico) {
            case "PSHI": return 0xF000;
            case "POPI": return 0xF200;
            case "PUSH": return 0xF400;
            case "POP":  return 0xF600;
            case "RETN": return 0xF800;
            case "SWAP": return 0xFA00;
            case "HALT": return 0xFFFF; // Parada do simulador
        }

        // Dado literal direto (ex: "DATA 10" ou apenas "10")
        if (mnemonico.equals("DATA")) {
            if (partes.length < 2) throw new RuntimeException("Linha " + numLinha + ": DATA exige um valor.");
            return resolverOperando(partes[1], numLinha) & 0xFFFF;
        }
        if (partes.length == 1) {
            return resolverOperando(mnemonico, numLinha) & 0xFFFF;
        }

        if (partes.length < 2) {
            throw new RuntimeException("Linha " + numLinha + ": operando ausente para '" + mnemonico + "'");
        }

        int operando = resolverOperando(partes[1], numLinha);

        // Instruções com operando de 12 bits (bits 11..0)
        switch (mnemonico) {
            case "LODD": return 0x0000 | (operando & 0x0FFF);
            case "STOD": return 0x1000 | (operando & 0x0FFF);
            case "ADDD": return 0x2000 | (operando & 0x0FFF);
            case "SUBD": return 0x3000 | (operando & 0x0FFF);
            case "JPOS": return 0x4000 | (operando & 0x0FFF);
            case "JZER": return 0x5000 | (operando & 0x0FFF);
            case "JUMP": return 0x6000 | (operando & 0x0FFF);
            case "LOCO": return 0x7000 | (operando & 0x0FFF);
            case "LODL": return 0x8000 | (operando & 0x0FFF);
            case "STOL": return 0x9000 | (operando & 0x0FFF);
            case "ADDL": return 0xA000 | (operando & 0x0FFF);
            case "SUBL": return 0xB000 | (operando & 0x0FFF);
            case "JNEG": return 0xC000 | (operando & 0x0FFF);
            case "JNZE": return 0xD000 | (operando & 0x0FFF);
            case "CALL": return 0xE000 | (operando & 0x0FFF);
            case "INSP": return 0xFC00 | (operando & 0x00FF);
            case "DESP": return 0xFE00 | (operando & 0x00FF);
            default:
                throw new RuntimeException("Linha " + numLinha + ": instrução desconhecida '" + mnemonico + "'");
        }
    }

    private int resolverOperando(String op, int numLinha) {
        String chave = op.toUpperCase();
        if (labels.containsKey(chave)) return labels.get(chave);
        try {
            if (chave.startsWith("0X")) return Integer.parseInt(chave.substring(2), 16);
            if (chave.startsWith("0B")) return Integer.parseInt(chave.substring(2), 2);
            return Integer.parseInt(chave);
        } catch (NumberFormatException e) {
            throw new RuntimeException("Linha " + numLinha + ": operando ou label inválido '" + op + "'");
        }
    }
}