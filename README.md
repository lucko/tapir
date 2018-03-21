# Tapir
JavaScript plugins using Nashorn for the Sponge API

![](https://i.imgur.com/rV6xnD9.png)


### Status

Still very much a WIP.

"Tapir" is just the name of the plugin. It's because tapirs are part of the same family as rhinos, which is where the name "nashorn" comes from.

This project uses [ScriptController](https://github.com/lucko/ScriptController) to handle most of the script loading logic.

### Why bother?

Aside from being a fun project to work on, there are some benefits to using Nashorn.

* You can reload scripts at runtime just by editing a file - like you would a config.
* Code can be very easily hotswapped - you can change the behaviour of a command/listener/whatever without restarting or reloading your server.

It's great for rapidly testing changes and playing with the API. You lose the advantages of type safety (and an ide that does most of the work for you) in return for being able to make changes within seconds.


### Example

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
        //source.gameMode().set(GameModes.CREATIVE);

        var message = Text.builder("you a big big dummy").color(TextColors.GREEN).build();
        source.sendMessage(message);

        return CommandResult.success();
    },
    "getSuggestions": function(source, arguments, targetPosition) {
        return ["foo", "bar"];
    },
    "testPermission": function(source) {
        return source.hasPermission("dummy.permission");
    },
    "shortDescription": "A short description.",
    "help": "<insert super useful message here>",
    "usage": "/dummy"
}

registerCommand(command, "dummy");
```
