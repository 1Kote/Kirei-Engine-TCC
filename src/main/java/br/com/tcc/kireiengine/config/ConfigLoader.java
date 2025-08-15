package br.com.tcc.kireiengine.config;

import br.com.tcc.kireiengine.config.model.Configuration;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileNotFoundException;
import java.io.FileReader;

public class ConfigLoader
{
    private static final Logger logger = LogManager.getLogger(ConfigLoader.class);

    /*
      Lê um arquivo de configuração JSON do caminho especificado e o converte em um objeto Configuration.
      @param path O caminho para o arquivo config.json.
      @return Um objeto Configuration preenchido com as regras, ou null em caso de erro.
     */

    public static Configuration loadConfig(String path)
    {
        Gson gson = new Gson();
        try
        {
            logger.info("Tentando carregar arquivo de configuração de: {}", path);
            FileReader reader = new FileReader(path);
            Configuration config = gson.fromJson(reader, Configuration.class);
            logger.info("Arquivo de configuração carregado com sucesso.");
            return config;
        }
        catch(FileNotFoundException e)
        {
            logger.error("ERRO CRÍTICO: Arquivo de configuração não encontrado no caminho: {}", path);
            logger.error("Por favor, certifique-se que o arquivo 'config.json' existe e o caminho está correto.");
            return null;
        }
        catch(JsonSyntaxException e)
        {
            logger.error("ERRO CRÍTICO: Arquivo 'config.gson' possuí um erro de sintaxe(Arquivo mal formatado).");
            logger.error("Detalhes do erro: {}", e.getMessage());
            return null;
        }
    }

}
