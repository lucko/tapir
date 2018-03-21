/*
 * This file is part of Tapir, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.tapir;

import com.google.inject.Inject;

import me.lucko.scriptcontroller.ScriptController;
import me.lucko.scriptcontroller.bindings.BindingsBuilder;
import me.lucko.scriptcontroller.bindings.BindingsSupplier;
import me.lucko.scriptcontroller.environment.ScriptEnvironment;
import me.lucko.scriptcontroller.environment.loader.ScriptLoadingExecutor;
import me.lucko.scriptcontroller.environment.script.Script;
import me.lucko.scriptcontroller.environment.settings.EnvironmentSettings;
import me.lucko.scriptcontroller.logging.SystemLogger;
import me.lucko.tapir.util.TapirEventSubscription;
import me.lucko.tapir.util.TapirCommand;

import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandCallable;
import org.spongepowered.api.command.CommandMapping;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.event.Event;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.scheduler.AsynchronousExecutor;
import org.spongepowered.api.scheduler.SpongeExecutorService;
import org.spongepowered.api.scheduler.SynchronousExecutor;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.serializer.TextSerializers;

import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner;
import jdk.nashorn.api.scripting.AbstractJSObject;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Plugin(
        id = "tapir",
        name = "Tapir",
        version = "1.0-SNAPSHOT",
        description = "JavaScript plugins using Nashorn",
        authors = {
                "Luck"
        }
)
public class Tapir {

    /**
     * The packages to import by default when each script is executed.
     * These strings are pattern specs for the FastClasspathScanner.
     */
    private static final String[] DEFAULT_IMPORTS = new String[]{
            // import all of the packages in the sponge api
            "org.spongepowered.api",
    };

    @Inject
    private Logger logger;

    @Inject
    @DefaultConfig(sharedRoot = false)
    private Path configFile;

    @Inject
    @DefaultConfig(sharedRoot = false)
    private ConfigurationLoader<CommentedConfigurationNode> configLoader;

    @Inject
    @SynchronousExecutor
    SpongeExecutorService syncExecutor;

    @Inject
    @AsynchronousExecutor
    SpongeExecutorService asyncExecutor;

    private ScriptController controller;
    private ScriptEnvironment environment;

    @Listener
    public void onServerStart(GameStartedServerEvent event) {
        this.logger.info("Loading configuration...");

        // create default config
        if (!Files.exists(this.configFile)) {
            CommentedConfigurationNode configuration = this.configLoader.createEmptyNode(
                    this.configLoader.getDefaultOptions().setHeader("Tapir - JavaScript plugins using Nashorn\nMade with <3 by Luck")
            );

            CommentedConfigurationNode directoryNode = configuration.getNode("script-directory");
            directoryNode.setValue("config/tapir/scripts/");
            directoryNode.setComment("The relative path of the scripts directory.");

            CommentedConfigurationNode initScriptNode = configuration.getNode("init-script");
            initScriptNode.setValue("init.js");
            initScriptNode.setComment("The name of the initial script.");

            CommentedConfigurationNode pollIntervalNode = configuration.getNode("poll-interval");
            pollIntervalNode.setValue(1);
            pollIntervalNode.setComment("How often script files should be polled for changes. Defined as a time in seconds.");

            try {
                this.configLoader.save(configuration);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        // load config
        CommentedConfigurationNode configuration;
        try {
            configuration = this.configLoader.load();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // search for packages which match the default import patterns
        this.logger.info("Scanning the classpath to resolve default package imports...");
        FastClasspathScanner classpathScanner = new FastClasspathScanner(DEFAULT_IMPORTS).strictWhitelist()
                .addClassLoader(Sponge.class.getClassLoader());

        // form a list of matches packages
        Set<String> defaultPackages = classpathScanner.scan()
                .getNamesOfAllClasses()
                .stream()
                .map(className -> {
                    // convert to a package name
                    return className.substring(0, className.lastIndexOf('.'));
                })
                .collect(Collectors.toSet());

        // setup the script controller
        this.logger.info("Initialising script controller...");
        this.controller = ScriptController.builder()
                .logger(new SpongeSystemLogger())
                .defaultEnvironmentSettings(EnvironmentSettings.builder()
                        .loadExecutor(ScriptLoadingExecutor.usingJavaScheduler(this.asyncExecutor))
                        .runExecutor(this.syncExecutor)
                        .pollRate(configuration.getNode("poll-interval").getLong(1), TimeUnit.SECONDS)
                        .initScript(configuration.getNode("init-script").getString("init.js"))
                        .withBindings(new SpongeScriptBindings())
                        .withBindings(new TapirScriptBindings())
                        .withDefaultPackageImports(defaultPackages)
                        .build()
                )
                .build();

        // get script directory
        Path scriptDirectory = Paths.get(configuration.getNode("script-directory").getString());
        if (!Files.isDirectory(scriptDirectory)) {
            try {
                Files.createDirectories(scriptDirectory);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // init a new environment for our scripts
        this.logger.info("Creating new script environment at " + scriptDirectory.toString() + " (" + scriptDirectory.toAbsolutePath().toString() + ")");
        this.environment = this.controller.setupNewEnvironment(scriptDirectory);

        this.logger.info("Done!");
    }

    /**
     * Script bindings for helper utilities
     */
    public static class SpongeScriptBindings implements BindingsSupplier {

        @Override
        public void supplyBindings(Script script, BindingsBuilder bindings) {
            // alias the registry
            bindings.put("registry", script.getClosables());

            // provide core server classes
            bindings.put("game", Sponge.getGame());
            bindings.put("server", Sponge.getServer());
            bindings.put("services", Sponge.getServiceManager());

            // some util functions
            bindings.put("formText", (Function<Object, Text>) SpongeScriptBindings::formText);
        }

        private static Text formText(Object object) {
            return TextSerializers.FORMATTING_CODE.deserialize(object.toString());
        }
    }

    private class TapirScriptBindings implements BindingsSupplier {

        @Override
        public void supplyBindings(Script script, BindingsBuilder bindings) {
            bindings.put("registerListener", new RegisterListenerFunction(script));
            bindings.put("registerCommand", new RegisterCommandFunction(script));
        }
    }

    private final class SpongeSystemLogger implements SystemLogger {
        @Override
        public void info(String s) {
            Tapir.this.logger.info(s);
        }

        @Override
        public void warning(String s) {
            Tapir.this.logger.warn(s);
        }

        @Override
        public void severe(String s) {
            Tapir.this.logger.error(s);
        }
    }

    private final class RegisterListenerFunction extends AbstractJSObject {
        private final Script script;

        private RegisterListenerFunction(Script script) {
            this.script = script;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Object call(Object thiz, Object... args) {
            Class<? extends Event> clazz = (Class<? extends Event>) args[0];
            Order order = Order.DEFAULT;
            ScriptObjectMirror consumer;

            Object arg = args[1];
            if (arg instanceof Order) {
                order = (Order) arg;
                consumer = (ScriptObjectMirror) args[2];
            } else {
                consumer = (ScriptObjectMirror) args[1];
            }

            TapirEventSubscription<?> subscription = new TapirEventSubscription<>(Tapir.this, clazz, order, (Consumer<Event>) event -> consumer.call(this, event));
            this.script.getClosables().bind(subscription);
            return subscription;
        }

        @Override
        public boolean isFunction() {
            return true;
        }
    }

    private final class RegisterCommandFunction extends AbstractJSObject {
        private final Script script;

        private RegisterCommandFunction(Script script) {
            this.script = script;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Object call(Object thiz, Object... args) {
            CommandCallable callable;

            Object handler = args[0];
            if (handler instanceof CommandCallable) {
                callable = (CommandCallable) handler;
            } else {
                ScriptObjectMirror mirror = (ScriptObjectMirror) handler;
                callable = new TapirCommand(mirror);
            }

            List<String> aliases = new ArrayList<>();
            for (int i = 1; i < args.length; i++) {
                aliases.add(args[i].toString());
            }

            CommandMapping mapping = Sponge.getCommandManager().register(Tapir.this, callable, aliases).get();
            this.script.getClosables().bind(() -> Sponge.getCommandManager().removeMapping(mapping));
            return mapping;
        }

        @Override
        public boolean isFunction() {
            return true;
        }
    }

}
