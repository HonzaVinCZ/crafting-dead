/*
 * Crafting Dead
 * Copyright (C) 2022  NexusNode LTD
 *
 * This Non-Commercial Software License Agreement (the "Agreement") is made between
 * you (the "Licensee") and NEXUSNODE (BRAD HUNTER). (the "Licensor").
 * By installing or otherwise using Crafting Dead (the "Software"), you agree to be
 * bound by the terms and conditions of this Agreement as may be revised from time
 * to time at Licensor's sole discretion.
 *
 * If you do not agree to the terms and conditions of this Agreement do not download,
 * copy, reproduce or otherwise use any of the source code available online at any time.
 *
 * https://github.com/nexusnode/crafting-dead/blob/1.18.x/LICENSE.txt
 *
 * https://craftingdead.net/terms.php
 */

package com.craftingdead.immerse.client.gui.screen.menu.play.list.server;

import java.util.Iterator;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import com.craftingdead.immerse.client.gui.screen.ConnectView;
import com.google.common.collect.Iterators;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;
import sm0keysa1m0n.bliss.view.ParentView;
import sm0keysa1m0n.bliss.view.TextView;
import sm0keysa1m0n.bliss.view.event.ActionEvent;

class ServerItemView extends ParentView {

  private static final Component QUESTION_MARK =
      new TextComponent("?").withStyle(ChatFormatting.DARK_GRAY);
  private static final Component ELLIPSES = new TextComponent("...");

  private final Iterator<String> animation = Iterators.cycle("O o o", "o O o", "o o O");

  private final ServerListView<?> list;
  private final ServerEntry serverEntry;

  private final TextView descriptionView;
  private final TextView pingView;
  private final TextView playerCountView;

  private long lastAnimationUpdateMs;

  @Nullable
  private Disposable pingTask;

  ServerItemView(ServerListView<?> list, ServerEntry serverEntry) {
    super(new Properties().styleClasses("item").doubleClick(true).focusable(true));

    this.list = list;
    this.serverEntry = serverEntry;

    this.descriptionView = new TextView(new Properties().id("motd")).setWrap(false);

    this.pingView = new TextView(new Properties().id("ping"));
    this.playerCountView = new TextView(new Properties().id("players"));

    this.addListener(ActionEvent.class, event -> this.connect());
    this.addChild(this.descriptionView);
    this.addChild(new TextView(new Properties().id("map"))
        .setText(new TextComponent(this.serverEntry.map() == null ? "-" : this.serverEntry.map())
            .withStyle(ChatFormatting.GRAY))
        .setWrap(false));
    this.addChild(this.pingView);
    this.addChild(this.playerCountView);

    this.ping();
  }

  @Override
  public void tick() {
    super.tick();
    long currentTime = Util.getMillis();
    if (this.lastAnimationUpdateMs != -1L && currentTime - this.lastAnimationUpdateMs >= 100L) {
      this.lastAnimationUpdateMs = currentTime;
      this.descriptionView.setText(new TextComponent(this.animation.next()));
    }
  }

  @Override
  public void keyPressed(int keyCode, int scanCode, int modifiers) {
    super.keyPressed(keyCode, scanCode, modifiers);
    if (keyCode == GLFW.GLFW_KEY_SPACE && this.isFocused()) {
      this.list.setSelectedItem(this);
    }
  }

  @Override
  public boolean mousePressed(double mouseX, double mouseY, int button) {
    if (this.isFocused()) {
      this.list.setSelectedItem(this);
    }
    return super.mousePressed(mouseX, mouseY, button);
  }

  @Override
  protected void removed() {
    super.removed();
    if (this.pingTask != null) {
      this.pingTask.dispose();
    }
  }

  @SuppressWarnings("removal")
  public void connect() {
    // Call this before creating a ConnectView instance.
    this.getScreen().keepOpen();
    this.minecraft.setScreen(
        ConnectView.createScreen(this.getScreen(), this.serverEntry.toServerAddress()));
  }

  public ServerEntry getServerEntry() {
    return this.serverEntry;
  }

  @SuppressWarnings("removal")
  public void ping() {
    if (this.pingTask != null && !this.pingTask.isDisposed()) {
      return;
    }

    this.descriptionView.setText(ELLIPSES);
    this.pingView.setText(ELLIPSES);
    this.playerCountView.setText(ELLIPSES);
    this.lastAnimationUpdateMs = 0;

    this.pingTask = this.list.getStatusProvider().checkStatus(this.serverEntry)
        .publishOn(Schedulers.fromExecutor(this.minecraft))
        .doOnNext(this::handlePingResult)
        .doOnError(PingError.class, this::handlePingError)
        .subscribe();
  }

  private void handlePingResult(VanillaServerStatusProvider.Result result) {
    var status = result.serverStatus();
    this.descriptionView.setText(status.getDescription() == null
        ? QUESTION_MARK
        : status.getDescription());
    if (result.responseTimeMs() >= 0) {
      var pingMs = result.responseTimeMs();
      ChatFormatting pingColor;
      if (pingMs < 200) {
        pingColor = ChatFormatting.GREEN;
      } else if (pingMs < 400) {
        pingColor = ChatFormatting.YELLOW;
      } else if (pingMs < 1200) {
        pingColor = ChatFormatting.RED;
      } else {
        pingColor = ChatFormatting.DARK_RED;
      }
      this.pingView.setText(new TextComponent(pingMs + "ms").withStyle(pingColor));
    } else {
      this.pingView.setText(QUESTION_MARK);
    }

    var players = status.getPlayers();
    this.playerCountView.setText(players == null
        ? QUESTION_MARK
        : formatPlayerCount(players.getNumPlayers(), players.getMaxPlayers()));

    this.lastAnimationUpdateMs = -1L;
  }

  private void handlePingError(PingError error) {
    this.descriptionView.setText(
        error.getDescription().copy().withStyle(ChatFormatting.RED));
    this.lastAnimationUpdateMs = -1L;
  }

  private static Component formatPlayerCount(int playerCount, int maxPlayerCount) {
    return new TextComponent(String.format("%,d", playerCount))
        .append(new TextComponent(" / ").withStyle(ChatFormatting.GRAY))
        .append(String.format("%,d", maxPlayerCount))
        .withStyle(ChatFormatting.WHITE);
  }
}
