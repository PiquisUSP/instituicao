package instituicao.web.dto;

// Corpo do POST /contas/{numero}/chaves. valor é dispensado para tipo ALEATORIA.
public record RegistrarChaveRequest(String tipo, String valor) {
}
