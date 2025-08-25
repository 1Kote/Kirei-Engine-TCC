package br.com.tcc.kireiengine.strategy;

import br.com.tcc.kireiengine.config.model.Configuration;
import br.com.tcc.kireiengine.config.model.MoveFilesRule;
import br.com.tcc.kireiengine.config.model.SeiriConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Estratégia que implementa a regra de negócio do Seiri para
 * identificar e mover arquivos com base na data da última modificação.
 */
public class SeiriOldFilesStrategy implements ScheduledTaskStrategy
{
    // Logger para registrar as operações desta estratégia.
    private static final Logger logger = LogManager.getLogger(SeiriOldFilesStrategy.class);

    /**
     * Executa a verificação de arquivos antigos nos diretórios monitorados.
     * @param config A configuração da aplicação.
     */
    @Override
    public void execute(Configuration config)
    {
        // Obtém o objeto de configuração do Seiri.
        SeiriConfig seiriConfig = config.getSeiriConfig();

        // Condicional para validar se a configuração do Seiri e suas regras estão presentes e habilitadas.
        if (seiriConfig == null || !seiriConfig.isEnabled() || seiriConfig.getRules() == null || seiriConfig.getRules().getMoveFilesNotAccessedForDays() == null)
        {
            // Se a validação falhar, a execução desta estratégia é abortada.
            logger.debug("SEIRI STRATEGY: Regra para mover arquivos antigos não está definida ou habilitada. Ignorando.");
            return;
        }

        // Obtém a regra específica de mover arquivos antigos.
        MoveFilesRule rule = seiriConfig.getRules().getMoveFilesNotAccessedForDays();
        // Condicional para verificar se esta regra específica está habilitada no config.json.
        if (!rule.isEnabled())
        {
            // Se não está, loga a informação e aborta a execução.
            logger.info("SEIRI STRATEGY: A regra para mover arquivos antigos está desabilitada.");
            return;
        }

        // Converte o valor 'days' da regra para o equivalente em milissegundos.
        long daysInMillis = TimeUnit.DAYS.toMillis(rule.getDays());
        // Obtém o timestamp atual do sistema em milissegundos.
        long currentTime = System.currentTimeMillis();

        // Log para informar o início da verificação com o parâmetro configurado.
        logger.info("[SEIRI STRATEGY] Verificando arquivos não modificados há {} dias...", rule.getDays());

        // Itera sobre cada diretório de monitoramento definido na configuração.
        for (String folderPath : config.getMonitorFolders())
        {
            try
            {
                // Percorre recursivamente todos os caminhos a partir do diretório base.
                Files.walk(Paths.get(folderPath))
                        // Filtra para processar apenas arquivos regulares.
                        .filter(Files::isRegularFile)
                        // Para cada arquivo encontrado, executa a lógica de verificação.
                        .forEach(path ->
                        {
                            try
                            {
                                // Obtém o timestamp da última modificação do arquivo.
                                long lastModifiedTime = Files.getLastModifiedTime(path).toMillis();
                                // Condicional para verificar se o tempo decorrido é maior que o limite definido na regra.
                                if ((currentTime - lastModifiedTime) > daysInMillis)
                                {
                                    // Se for, loga a ação que será tomada.
                                    logger.info("[SEIRI STRATEGY] Arquivo {} é considerado antigo. Movendo...", path);
                                    // Chama o método para mover o arquivo para o destino de quarentena.
                                    moveFile(path, rule.getDestination());
                                }
                            }
                            // Captura exceções de I/O que podem ocorrer ao ler os atributos de um arquivo.
                            catch (IOException e)
                            {
                                logger.error("[SEIRI STRATEGY] Erro ao obter atributos do arquivo {}", path, e);
                            }
                        });
            }
            // Captura exceções de I/O que podem ocorrer ao tentar percorrer um diretório.
            catch (IOException e)
            {
                logger.error("[SEIRI STRATEGY] Erro ao percorrer o diretório {}", folderPath, e);
            }
        }
    }

    /**
     * Move um arquivo, criando uma subpasta de destino baseada na sua extensão.
     * @param source O arquivo de origem.
     * @param baseDestinationFolder A pasta de destino principal.
     */
    private void moveFile(Path source, String baseDestinationFolder)
    {
        // Extrai a extensão do arquivo para usar como nome da subpasta.
        getFileExtension(source.toString()).ifPresent(extension ->
        {
            try
            {
                // Constrói o caminho da subpasta (ex: /.../Quarentena/PDF).
                Path destinationPath = Paths.get(baseDestinationFolder, extension.toUpperCase());

                // Verifica se a subpasta de destino não existe.
                if (Files.notExists(destinationPath))
                {
                    // Se não existe, cria a estrutura de diretórios.
                    Files.createDirectories(destinationPath);
                }

                // Define o caminho final do arquivo dentro da subpasta.
                Path target = destinationPath.resolve(source.getFileName());

                // Verifica se um arquivo com o mesmo nome já existe no destino.
                if (Files.exists(target))
                {
                    // Se existe, gera um novo nome único.
                    target = generateUniqueFileName(target);
                }

                // Move o arquivo da origem para o destino.
                Files.move(source, target);
                // Log de sucesso.
                logger.info("SUCESSO: Arquivo '{}' movido para '{}'", source.getFileName(), target);
            }
            // Captura exceções que podem ocorrer durante a operação de movimentação.
            catch (Exception e)
            {
                logger.error("FALHA: Não foi possível mover o arquivo '{}'.", source, e);
            }
        });
    }

    /**
     * Gera um nome de arquivo único para evitar sobrescrita.
     * @param target O caminho de destino original.
     * @return Um novo caminho com um nome único.
     */
    private Path generateUniqueFileName(Path target)
    {
        String fileName = target.getFileName().toString();
        String baseName = fileName;
        String extension = "";
        int dotIndex = fileName.lastIndexOf('.');

        if (dotIndex > 0)
        {
            baseName = fileName.substring(0, dotIndex);
            extension = fileName.substring(dotIndex);
        }

        int counter = 1;
        Path uniqueTarget = target;
        while (Files.exists(uniqueTarget))
        {
            String uniqueName = baseName + "_" + counter + extension;
            uniqueTarget = target.getParent().resolve(uniqueName);
            counter++;
        }
        return uniqueTarget;
    }

    /**
     * Extrai a extensão de um nome de arquivo.
     * @param fileName O nome completo do arquivo.
     * @return Um Optional com a extensão.
     */
    private Optional<String> getFileExtension(String fileName)
    {
        return Optional.ofNullable(fileName)
                .filter(f -> f.contains("."))
                .map(f -> f.substring(fileName.lastIndexOf(".") + 1).toLowerCase());
    }
}