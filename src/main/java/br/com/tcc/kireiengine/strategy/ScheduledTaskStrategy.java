package br.com.tcc.kireiengine.strategy;

import br.com.tcc.kireiengine.config.model.Configuration;

//Interface Strategy para tarefas agendadas (Seiri/Seiso)

public interface ScheduledTaskStrategy
{
    /**
     * Executa a tarefa agendada
     * @param config Configuração da aplicação
     */
    void execute(Configuration config);
}