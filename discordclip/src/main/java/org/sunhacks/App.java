package org.sunhacks;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.managers.AudioManager;

import java.io.File;

import javax.security.auth.login.LoginException;

import java.util.EnumSet;

/**
 * Hello world!
 *
 */
public class App extends ListenerAdapter {

    public static void main(String[] args) throws LoginException {
        JDA jda = JDABuilder.createDefault(System.getenv("TOKEN")) // slash commands don't need any intents
                .addEventListeners(new App()).build();

        System.out.println(jda.getInviteUrl(EnumSet.noneOf(Permission.class)));
        // These commands take up to an hour to be activated after
        // creation/update/delete
        //CommandListUpdateAction commands = jda.updateCommands();

        // Moderation commands with required options
        // commands.addCommands(new CommandData("join", "Joins the voice channel you are
        // connected in"));

        /*commands.addCommands(new CommandData("clip", "Clip your voice chat")
            .addOptions(new OptionData(OptionType.INTEGER, "time", "The amount of time you want to clip in seconds")
                .addChoice("5 Seconds", 5)
                .addChoice("15 Seconds", 15)
                .addChoice("30 Seconds", 30)
                .addChoice("60 Seconds", 60))
        );*/

        // Send the new set of commands to discord, this will override any existing
        // global commands with the new set provided here
        //commands.queue();
    }

    @Override
    public void onSlashCommand(SlashCommandEvent event) {
        System.out.println(event.getGuild().getSelfMember().hasPermission(Permission.MESSAGE_WRITE));
        // Only accept user commands
        if (event.getUser().isBot())
            return;
        // Only accept commands from guilds
        if (event.getGuild() == null)
            return;
        switch (event.getName()) {
            case "join":
                VoiceChannel voiceChannel = event.getMember().getVoiceState().getChannel();
                AudioManager audioManager = event.getGuild().getAudioManager();
                // User is not in a voice channel
                if (voiceChannel == null) {
                    event.reply("You are not connected to a voice channel!").setEphemeral(true).queue();
                    return;
                } else if (!event.getGuild().getSelfMember().hasPermission(voiceChannel, Permission.VOICE_CONNECT)) {
                    event.reply("I am not allowed to join voice channels").setEphemeral(true).queue();
                } else {
                    if (event.getGuild().getSelfMember().getVoiceState().getChannel() != null) {
                        audioManager.closeAudioConnection();
                        event.reply("Left").setEphemeral(true).queue();
                    } else {
                        voiceChannel.getBitrate();
                        audioManager.openAudioConnection(voiceChannel);
                        // initalize the audio reciever listener
                        audioManager.setReceivingHandler(new AudioReceiveListener(1, voiceChannel));
                        event.reply("Joined").queue();
                    }
                }
                break;
            case "clip":
                int time = Integer.parseInt(event.getOption("time").getAsString());
                File file = ((AudioReceiveListener) event.getGuild().getAudioManager().getReceivingHandler()).createFile(time);
                event.reply("Here is your recording:").addFile(file).queue();
                
                break;
            default:
                event.reply("I can't handle that command right now :(").setEphemeral(true).queue();
        }
    }

    @Override
    public void onButtonClick(ButtonClickEvent event) {
        // users can spoof this id so be careful what you do with this
        String[] id = event.getComponentId().split(":"); // this is the custom id we specified in our button
        String authorId = id[0];
        String type = id[1];
        // When storing state like this is it is highly recommended to do some kind of
        // verification that it was generated by you, for instance a signature or local
        // cache
        if (!authorId.equals(event.getUser().getId()))
            return;
        event.deferEdit().queue(); // acknowledge the button was clicked, otherwise the interaction will fail

        MessageChannel channel = event.getChannel();
        switch (type) {
            case "prune":
                int amount = Integer.parseInt(id[2]);
                event.getChannel().getIterableHistory().skipTo(event.getMessageIdLong()).takeAsync(amount)
                        .thenAccept(channel::purgeMessages);
                // fallthrough delete the prompt message with our buttons
            case "delete":
                event.getHook().deleteOriginal().queue();
        }
    }

    public void ban(SlashCommandEvent event, User user, Member member) {
        event.deferReply(true).queue(); // Let the user know we received the command before doing anything else
        InteractionHook hook = event.getHook(); // This is a special webhook that allows you to send messages without
                                                // having permissions in the channel and also allows ephemeral messages
        hook.setEphemeral(true); // All messages here will now be ephemeral implicitly
        if (!event.getMember().hasPermission(Permission.BAN_MEMBERS)) {
            hook.sendMessage("You do not have the required permissions to ban users from this server.").queue();
            return;
        }

        Member selfMember = event.getGuild().getSelfMember();
        if (!selfMember.hasPermission(Permission.BAN_MEMBERS)) {
            hook.sendMessage("I don't have the required permissions to ban users from this server.").queue();
            return;
        }

        if (member != null && !selfMember.canInteract(member)) {
            hook.sendMessage("This user is too powerful for me to ban.").queue();
            return;
        }

        int delDays = 0;
        OptionMapping option = event.getOption("del_days");
        if (option != null) // null = not provided
            delDays = (int) Math.max(0, Math.min(7, option.getAsLong()));
        // Ban the user and send a success response
        event.getGuild().ban(user, delDays).flatMap(v -> hook.sendMessage("Banned user " + user.getAsTag())).queue();
    }

    public void say(SlashCommandEvent event, String content) {
        event.reply(content).queue(); // This requires no permissions!
    }

    public void leave(SlashCommandEvent event) {
        if (!event.getMember().hasPermission(Permission.KICK_MEMBERS))
            event.reply("You do not have permissions to kick me.").setEphemeral(true).queue();
        else
            event.reply("Leaving the server... :wave:") // Yep we received it
                    .flatMap(v -> event.getGuild().leave()) // Leave server after acknowledging the command
                    .queue();
    }

    public void prune(SlashCommandEvent event) {
        OptionMapping amountOption = event.getOption("amount"); // This is configured to be optional so check for null
        int amount = amountOption == null ? 100 // default 100
                : (int) Math.min(200, Math.max(2, amountOption.getAsLong())); // enforcement: must be between 2-200
        String userId = event.getUser().getId();
        event.reply("This will delete " + amount + " messages.\nAre you sure?") // prompt the user with a button menu
                .addActionRow(// this means "<style>(<id>, <label>)" the id can be spoofed by the user so
                              // setup some kinda verification system
                        Button.secondary(userId + ":delete", "Nevermind!"),
                        Button.danger(userId + ":prune:" + amount, "Yes!")) // the first parameter is the component id
                                                                            // we use in onButtonClick above
                .queue();
    }
}
