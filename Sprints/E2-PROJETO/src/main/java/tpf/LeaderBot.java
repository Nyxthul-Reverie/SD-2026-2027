package tpf;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import java.util.Set;

public class LeaderBot extends ListenerAdapter {
    private static final Set<String> ADMIN_COMMANDS = Set.of("config", "replica-token", "replica-remover", "rajada");
    private static final String ADMIN_ROLE = "admin-arquivo";
    private static final String CHANNEL_NAME = "arquivo";

    public static void main(String[] args) throws InterruptedException {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        String token = dotenv.get("DISCORD_TOKEN");
        String guildId = dotenv.get("DISCORD_GUILD_ID");

        if(token == null || token.isBlank()){
            throw new IllegalStateException("O token do bot não está definido.");
        } else if(guildId == null || guildId.isBlank()){
            throw new IllegalStateException("O id da Guild não está definido.");
        }
        JDA jda = JDABuilder.createDefault(token).enableIntents(GatewayIntent.MESSAGE_CONTENT).addEventListeners(new LeaderBot()).build();
        jda.awaitReady();

        Guild guild = jda.getGuildById(guildId);
        if(guild == null){
            throw new IllegalStateException("O bot não encontrou o servidor indicado.");
        }

        guild.upsertCommand("estado", "Mostra o estado do líder").queue();
        guild.upsertCommand("registar", "Registar").addOption(OptionType.STRING, "texto", "texto a registar", true).queue();
        guild.upsertCommand("config", "Configurar o numero de réplicas").addOption(OptionType.INTEGER, "r", "Número de réplicas", true).queue();
        guild.upsertCommand("recuperar", "Recupera registos num intervalo").addOption(OptionType.INTEGER, "de", "Primeiro identificador do intervalo", true).addOption(OptionType.INTEGER, "ate", "Último identificador do intervalo", true).queue();
        guild.upsertCommand("replica-token", "Token da replica").queue();
        guild.upsertCommand("replica-remover", "Remover replica").addOption(OptionType.STRING, "id", "Identificador da Réplica", true).queue();
        guild.upsertCommand("rajada", "Modo rajada").addOption(OptionType.INTEGER, "n", "Número de Registos", true).addOption(OptionType.INTEGER, "intervalo-ms", "Intervalo em milissegundos", true).queue();

        System.out.println("Bot Online.\n Name: " + jda.getSelfUser().getName());
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event){
        var member = event.getMember();
        var eventChannel = event.getChannel().getName();
        String channel = eventChannel.trim();

        if(member == null || !channel.equalsIgnoreCase(CHANNEL_NAME) ){
            return;
        }

        Boolean isAdmin = false;
        for(var role : member.getRoles()){
            if(role.getName().equalsIgnoreCase(ADMIN_ROLE)){
                isAdmin = true;
                break;
            }
        }

        String command = event.getName();
        if(ADMIN_COMMANDS.contains(command) && !isAdmin){
            event.reply("Sem permissão: este comando exige a role `" + ADMIN_ROLE +"`.").setEphemeral(true).queue();
            return;
        }
        switch(command) {
            case "estado", "replica-token" -> event.reply("`/" + command + "` recebido - ainda nao implementado.").queue();
            case "rajada" -> handleRajada(event);
            case "config" -> handleConfig(event);
            case "registar" -> handleRegistar(event); 
            case "recuperar" -> handleRecuperar(event);
            case "replica-remover" -> handleReplicaRemover(event);
            default -> event.reply("Comando desconhecido.").queue();
        }
    }

    private void handleRegistar(SlashCommandInteractionEvent event){
        var option = event.getOption("texto");

        if(option == null){
            event.reply("Argumento Inválido: falta `texto`.").setEphemeral(true).queue();
            return;
        }

        String text = option.getAsString().trim();

        if(text.isBlank()){
            event.reply("Argumento inválido: `texto` não pode estar vazio.").setEphemeral(true).queue();
            return;
        }

        event.reply("`/registar` recebido: texto=\"" + text + "\" - ainda não implementado.").queue();
    }

    private void handleConfig(SlashCommandInteractionEvent event){
        var option = event.getOption("r");

        if(option == null){
            event.reply("Argumento Inválido: falta `r`.").setEphemeral(true).queue();
            return;
        }

        long r = option.getAsLong();

        if(r <= 0){
            event.reply("Argumento inválido: `r` tem que ser maior que 0.").setEphemeral(true).queue();
            return;
        }

        event.reply("`/config` recebido: r=" + r + " - ainda não implementado.").queue();
    }

    private void handleRecuperar(SlashCommandInteractionEvent event){
        var optionDe = event.getOption("de");
        var optionAte = event.getOption("ate");

        if(optionDe == null || optionAte == null){
            event.reply("Argumento Inválido: falta `de` e `ate`.").setEphemeral(true).queue();
            return;
        }

        long de = optionDe.getAsLong();
        long ate = optionAte.getAsLong();

        if(de < 0 || ate < 0){
            event.reply("Argumentos inválidos: `de` e `ate` não podem ser negativos.").setEphemeral(true).queue();
            return;
        }

        if(de > ate){
            event.reply("Intervalo inválido: `de` tem de ser menor ou igual que `ate`.").setEphemeral(true).queue();
            return;
        }

        event.reply("`/recuperar` recebido: de=" + de + ", ate=" + ate + " - ainda não implementado.").queue();
    }

    private void handleReplicaRemover(SlashCommandInteractionEvent event){
        var option = event.getOption("id");

        if(option == null){
            event.reply("Argumento Inválido: falta `id`.").setEphemeral(true).queue();
            return;
        }

        String id = option.getAsString().trim();

        if(id.isBlank()){
            event.reply("Argumento inválido: `id` não pode estar vazio.").setEphemeral(true).queue();
            return;
        }

        event.reply("`/replica-remover` recebido: id=\"" + id + "\" - ainda não implementado.").queue();

    }

    private void handleRajada(SlashCommandInteractionEvent event){
        var optionN = event.getOption("n");
        var optionMs = event.getOption("intervalo-ms");

        if(optionN == null || optionMs == null){
            event.reply("Argumento Inválido: falta `n` e `intervalo-ms`.").setEphemeral(true).queue();
            return;
        }

        long n = optionN.getAsLong();
        long ms = optionMs.getAsLong();

        if(n <= 0){
            event.reply("Argumentos inválidos: `n` tem que ser maior que 0.").setEphemeral(true).queue();
            return;
        }

        if(ms < 0){
            event.reply("Argumentos inválidos: `intervalo-ms` não pode ser negativo.").setEphemeral(true).queue();
            return;
        }

        event.reply("`/rajada` recebido: n=" + n + ", intervaloMs=" + ms + " - ainda não implementado.").queue();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event){
        var author = event.getAuthor();
        var eventChannel = event.getChannel().getName();
        String channel = eventChannel.trim();
    
        if(author.isBot() || !event.isFromGuild() || !channel.equalsIgnoreCase(CHANNEL_NAME)){
            return;
        }

        String mensagem = event.getMessage().getContentRaw();

        event.getMessage().reply("Mensagem recebida: " + mensagem).queue();
    }
}