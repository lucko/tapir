![tapir](https://i.imgur.com/J1aNl5U.png)

tapir is a script-loading system which lets you create JavaScript plugins for the Sponge API.

### about

* tapir uses the Nashorn JavaScript library (introduced in Java 8) to handle execution of the JS code
* [ScriptController](https://github.com/lucko/ScriptController) is used to handle most of the script loading logic.

### features

* Write code in JavaScript using all of the same APIs you'd have access to in a Java plugin!
* Changes to script files are detected and applied automagically - just hit the save button "save" on your text editor
* Code can be hotswapped at runtime - you can completely change the behaviour of a command/listener/whatever without restarting or reloading the server.

### the not-so-good bits

* It's JavaScript - that means no type safety, no IDEs that autocomplete most things for you, no way to know that your code is going to run properly before you actually try running it.
* If you want script reloading to work nicely, you have to program with it in mind. State that should be preserved between reloads has to be exported, and any custom listeners or registrations have to be bound to the script instance. It's not hard or time-consuming to do, you've just got to remember to do it! 

### examples

Example of a simple event listener and command.

```javascript
logger.info("Hello World!");

registerListener(ClientConnectionEvent.Join.class, Order.EARLY, function(e) {
    var player = e.getTargetEntity();
    var message = Text.builder("Hello and welcome to my server!").color(TextColors.GREEN).build();
    player.sendMessage(message);
})

var command = {
    "process": function(source, arguments) {
        source.gameMode().set(GameModes.CREATIVE);
        
        var message = Text.builder("You are now in creative mode!").color(TextColors.GREEN).build();
        source.sendMessage(message);

        return CommandResult.success();
    },
    "getSuggestions": function(source, arguments, targetPosition) {
        return ["foo", "bar"];
    },
    "testPermission": function(source) {
        return source.hasPermission("myplugin.cheat");
    },
    "shortDescription": "Enable a super-secret cheat mode. Do it, I dare you!",
    "help": "<insert super useful help message here>",
    "usage": "/cheat"
}

registerCommand(command, "cheat");
```
