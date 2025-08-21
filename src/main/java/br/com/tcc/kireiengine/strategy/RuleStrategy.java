package br.com.tcc.kireiengine.strategy;

import java.nio.file.Path;

/**
 * Contrato (Interface) para o Padrão de Projeto Strategy.
 *
 * Define o método fundamental que todas as estratégias de regras (Seiton, Seiri, etc.)
 * devem implementar. Cada classe que implementar esta interface representará
 * uma regra de negócio específica para processar um arquivo.
 */
public interface RuleStrategy
{
    /**
     * Aplica a lógica da regra a um determinado arquivo.
     * @param filePath O caminho completo do arquivo a ser processado.
     */
    void apply(Path filePath);
}