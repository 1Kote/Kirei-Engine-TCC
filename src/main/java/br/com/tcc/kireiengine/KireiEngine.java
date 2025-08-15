package br.com.tcc.kireiengine;

import br.com.tcc.kireiengine.config.ConfigLoader;
import br.com.tcc.kireiengine.config.model.Configuration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class KireiEngine
{
    private static final Logger logger = LogManager.getLogger(KireiEngine.class);
    private static final String CONFIG_FILE_PATH = "src/main/resources/config.json";
    public static void main(String[] args)
    {
        logger.info("========================================");
        logger.info("    Kirei Engine - Iniciando Serviço    ");
        logger.info("========================================");

        //Carrega a configuração do config.json
        Configuration config = ConfigLoader.loadConfig(CONFIG_FILE_PATH);

        //Validação da configuração
        if (config == null)
        {
            logger.fatal("Não foi possivel carregar o arquivo de configuração. Encerrando o Serviço.");
            //Encerra a aplicação
            System.exit(1);
        }

        logger.info("Configuração carregada com sucesso! {} regras de Seiton encontradas", config.getSeitonRules().size());

        logger.info("Kirei Engine está em execução");


    }
}