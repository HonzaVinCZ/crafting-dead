package net.rocketpowered.connector.client.gui.guild;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.bson.types.ObjectId;
import org.jetbrains.annotations.Nullable;
import com.craftingdead.immerse.client.gui.screen.Theme;
import com.google.common.collect.Sets;
import io.github.humbleui.skija.FontMgr;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.rocketpowered.api.Rocket;
import net.rocketpowered.common.payload.GuildInvitePayload;
import net.rocketpowered.common.payload.GuildMemberPayload;
import net.rocketpowered.common.payload.GuildPayload;
import net.rocketpowered.common.payload.UserPayload;
import net.rocketpowered.common.payload.UserPresencePayload;
import net.rocketpowered.connector.client.gui.RocketToast;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import sm0keysa1m0n.bliss.Color;
import sm0keysa1m0n.bliss.view.ParentView;
import sm0keysa1m0n.bliss.view.TextView;
import sm0keysa1m0n.bliss.view.View;

public class InvitesView extends ParentView {

  public static final Component TITLE = new TranslatableComponent("view.guild.invites");

  private final ParentView invitesListView;

  private final ParentView controlsView;

  private final View acceptButton;
  private final View declineButton;

  private final Map<ObjectId, InviteView> inviteViews = new HashMap<>();

  @Nullable
  private GuildPayload guild;

  @Nullable
  private InviteView selectedInviteView;

  private Disposable listener;

  private Set<GuildInvitePayload> lastInvites = Collections.emptySet();

  @SuppressWarnings("removal")
  public InvitesView() {
    super(new Properties().styleClasses("page", "blur"));

    this.addChild(new TextView(new Properties().id("title")).setText(TITLE));

    this.invitesListView = new ParentView(new Properties().id("list"));

    this.addChild(this.invitesListView);

    this.controlsView = new ParentView(new Properties().id("controls"));
    this.controlsView.addChild(
        this.acceptButton = Theme.createBlueButton(
            new TextComponent("Accept"),
            () -> Rocket.getGameClientGateway()
                .ifPresentOrElse(connection -> {
                  var inviteGuild = this.selectedInviteView.invite.guild();
                  connection
                      .acceptGuildInvite(inviteGuild.id())
                      .publishOn(Schedulers.fromExecutor(this.minecraft))
                      .doOnSuccess(__ -> RocketToast.info(this.minecraft,
                          "Welcome to " + inviteGuild.name()))
                      .doOnError(error -> RocketToast.error(this.minecraft, error.getMessage()))
                      .subscribe();
                }, () -> RocketToast.info(this.minecraft, "Not connected to Rocket"))));
    this.controlsView.addChild(
        this.declineButton = Theme.createRedButton(
            new TextComponent("Decline"),
            () -> Rocket.getGameClientGateway()
                .ifPresentOrElse(connection -> connection
                    .declineGuildInvite(this.selectedInviteView.invite.guild().id())
                    .publishOn(Schedulers.fromExecutor(this.minecraft))
                    .doOnError(error -> RocketToast.error(this.minecraft, error.getMessage()))
                    .subscribe(),
                    () -> RocketToast.info(this.minecraft, "Not connected to Rocket"))));

    this.addChild(this.controlsView);


    this.updateSelected();
  }

  protected void updateSelected() {
    this.selectedInviteView = this.invitesListView.getChildren().stream()
        .filter(InviteView.class::isInstance)
        .map(InviteView.class::cast)
        .filter(View::isFocused)
        .findAny()
        .orElse(null);

    this.acceptButton.setEnabled(this.selectedInviteView != null);
    this.declineButton.setEnabled(this.selectedInviteView != null);
  }

  @SuppressWarnings("removal")
  @Override
  protected void added() {
    super.added();
    this.listener = Rocket.getGameClientGatewayFeed()
        .flatMap(api -> api.getSocialProfileFeed()
            .publishOn(Schedulers.fromExecutor(this.minecraft))
            .doOnNext(profile -> {
              Sets.difference(this.lastInvites, profile.guildInvites())
                  .forEach(invite -> {
                    InviteView view = this.inviteViews.remove(invite.guild().id());
                    if (view != null && view.hasParent()) {
                      this.invitesListView.removeChild(view);
                    }
                  });

              profile.guildInvites().forEach(invite -> {
                InviteView view =
                    this.inviteViews.computeIfAbsent(invite.guild().id(),
                        __ -> new InviteView(invite));
                if (!view.hasParent()) {
                  this.invitesListView.addChild(view);
                }
              });

              this.invitesListView.layout();
              this.lastInvites = profile.guildInvites();
            }))
        .subscribeOn(Schedulers.boundedElastic())
        .subscribe();
  }

  @Override
  protected void removed() {
    super.removed();
    this.listener.dispose();
  }

  @Override
  public boolean mousePressed(double mouseX, double mouseY, int button) {
    this.updateSelected();
    return super.mousePressed(mouseX, mouseY, button);
  }

  private static class InviteView extends ParentView {

    private final GuildInvitePayload invite;

    private final TextView totalMembersView;
    private final TextView onlineMemebrsView;
    private final TextView offlineMemebrsView;

    private Disposable memberListener;

    public InviteView(GuildInvitePayload invite) {
      super(new Properties().styleClasses("item").unscaleBorder(false).focusable(true));

      this.invite = invite;

      this.totalMembersView = new TextView(new Properties());
      this.onlineMemebrsView = new TextView(new Properties());
      this.offlineMemebrsView = new TextView(new Properties());

      this.addChild(new TextView(new Properties())
          .setText(new TextComponent(this.invite.guild().name())
              .withStyle(ChatFormatting.BOLD)));

      var playerCountsView = new ParentView(new Properties().id("player-counts"));

      this.addChild(playerCountsView);
      playerCountsView.addChild(this.totalMembersView);
      playerCountsView.addChild(new TextView(new Properties())
          .setText(new TextComponent(" | ")));
      playerCountsView.addChild(this.onlineMemebrsView);
      playerCountsView.addChild(new TextView(new Properties())
          .setText(new TextComponent(" | ")));
      playerCountsView.addChild(this.offlineMemebrsView);
      if (this.invite.sender() != null) {
        this.addChild(new TextView(new Properties())
            .setText(
                new TextComponent("Invited by " + this.invite.sender().minecraftProfile().name())
                    .withStyle(ChatFormatting.ITALIC)));
      }
    }

    @Override
    public void styleRefreshed(FontMgr fontManager) {
      this.onlineMemebrsView.getStyle().color.defineState(Theme.ONLINE);
      this.offlineMemebrsView.getStyle().color.defineState(Color.GRAY);
    }

    @SuppressWarnings("removal")
    @Override
    protected void added() {
      super.added();
      AtomicInteger counter = new AtomicInteger();
      this.memberListener = Rocket.getGameClientGatewayFeed()
          .flatMap(api -> Mono
              .fromRunnable(() -> this.minecraft.executeBlocking(() -> {
                this.onlineMemebrsView.setText("0 Online");
                this.offlineMemebrsView.setText("0 Offline");
              }))
              .thenMany(api.getGuildMembers(this.invite.guild().id()))
              .doOnNext(__ -> counter.incrementAndGet())
              .doOnComplete(() -> {
                int count = counter.getAndSet(0);
                if (count == 1) {
                  this.totalMembersView.setText("1 Member");
                } else {
                  this.totalMembersView.setText(count + " Members");
                }
              })
              .map(GuildMemberPayload::user)
              .map(UserPayload::id)
              .flatMap(userId -> api.getUserPresenceFeed(userId).next())
              .groupBy(UserPresencePayload::online)
              .publishOn(Schedulers.fromExecutor(this.minecraft))
              .delayUntil(group -> group.count()
                  .doOnNext(count -> {
                    if (group.key()) {
                      this.onlineMemebrsView.setText(count + " Online");
                    } else {
                      this.offlineMemebrsView.setText(new TextComponent(count + " Offline"));
                    }
                  }))
              // Update member counts every minute.
              .repeatWhen(flux -> Flux.interval(Duration.ofMinutes(1L))))
          .subscribeOn(Schedulers.boundedElastic())
          .subscribe();
    }

    @Override
    protected void removed() {
      super.removed();
      this.memberListener.dispose();
    }
  }
}
