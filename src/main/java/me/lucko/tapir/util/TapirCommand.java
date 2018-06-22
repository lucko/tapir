/*
 * This file is part of tapir, licensed under the MIT License.
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

package me.lucko.tapir.util;

import com.google.common.base.Preconditions;

import org.spongepowered.api.command.CommandCallable;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import jdk.nashorn.api.scripting.ScriptObjectMirror;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

@SuppressWarnings("unchecked")
public class TapirCommand implements CommandCallable {
    private final ScriptObjectMirror scriptObject;

    public TapirCommand(ScriptObjectMirror scriptObject) {
        this.scriptObject = scriptObject;
        Preconditions.checkState(this.scriptObject.hasMember("process"), "object is missing a 'process' member");
    }

    @Override
    public CommandResult process(CommandSource source, String arguments) {
        return (CommandResult) this.scriptObject.callMember("process", source, arguments);
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String arguments, @Nullable Location<World> targetPosition) {
        if (this.scriptObject.hasMember("getSuggestions")) {
            return (List<String>) this.scriptObject.callMember("getSuggestions", source, arguments, targetPosition);
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public boolean testPermission(CommandSource source) {
        if (this.scriptObject.hasMember("testPermission")) {
            return (boolean) this.scriptObject.callMember("testPermission", source);
        } else {
            return true;
        }
    }

    @Override
    public Optional<Text> getShortDescription(CommandSource source) {
        return getTextMember("shortDescription");
    }

    @Override
    public Optional<Text> getHelp(CommandSource source) {
        return getTextMember("help");
    }

    @Override
    public Text getUsage(CommandSource source) {
        return getTextMember("usage").orElse(Text.of("unknown"));
    }

    private Optional<Text> getTextMember(String memberName) {
        if (this.scriptObject.hasMember(memberName)) {
            return Optional.empty();
        }

        Object val = this.scriptObject.getMember(memberName);
        if (val == null) {
            return Optional.empty();
        }

        if (val instanceof Text) {
            return Optional.of(((Text) val));
        }

        return Optional.of(Text.of(val));
    }
}
