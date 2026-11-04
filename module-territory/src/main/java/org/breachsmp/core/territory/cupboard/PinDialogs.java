package org.breachsmp.core.territory.cupboard;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.breachsmp.core.api.concurrent.CoreExecutors;
import org.breachsmp.core.domain.territory.PinHasher;
import org.breachsmp.core.territory.claim.ClaimService;
import org.breachsmp.core.territory.claim.LiveClaim;
import org.breachsmp.core.territory.claim.PinResult;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dialog Paper per il PIN dei Tool Cupboard (D-37). Il callback del pulsante arriva fuori dal main thread: si torna sul main
 * con {@code executors.main()} prima di toccare {@link ClaimService}.
 */
public final class PinDialogs {

    private static final String INPUT = "pin";
    private static final ClickCallback.Options ONCE =
            ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(2)).build();

    private final ClaimService claims;
    private final CoreExecutors executors;
    private final Logger logger;

    public PinDialogs(ClaimService claims, CoreExecutors executors, Logger logger) {
        this.claims = Objects.requireNonNull(claims, "claims");
        this.executors = Objects.requireNonNull(executors, "executors");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Un estraneo prova il PIN; se è giusto diventa autorizzato. */
    public void askPin(Player player, LiveClaim claim) {
        player.showDialog(dialog("Inserisci il PIN", "Il PIN del Tool Cupboard autorizza a costruire in questa base.",
                "Entra", pin -> tryPin(player, claim, pin)));
    }

    /** Un autorizzato imposta o cambia il PIN. */
    public void askNewPin(Player player, LiveClaim claim) {
        player.showDialog(dialog("Imposta il PIN", "Da 4 a 8 cifre. Chi lo conosce può autorizzarsi nella base.",
                "Salva", pin -> setPin(player, claim, pin)));
    }

    private void tryPin(Player player, LiveClaim claim, String pin) {
        if (!validFormat(player, pin)) {
            return;
        }
        claims.tryPin(claim.id(), player.getUniqueId(), pin).whenCompleteAsync((result, error) -> {
            if (error != null) {
                fail(player, error, "Impossibile verificare il PIN, riprova.");
                return;
            }
            switch (result) {
                case AUTHORIZED -> ok(player, "PIN corretto: sei autorizzato.");
                case ALREADY_AUTHORIZED -> ok(player, "Sei già autorizzato in questa base.");
                case WRONG_PIN -> deny(player, "PIN errato.");
                case NO_PIN_SET -> deny(player, "Questa base non ha ancora un PIN.");
                case RATE_LIMITED -> deny(player, "Troppi tentativi: riprova più tardi.");
            }
        }, executors.main());
    }

    private void setPin(Player player, LiveClaim claim, String pin) {
        if (!validFormat(player, pin)) {
            return;
        }
        claims.setPin(claim.id(), pin, player.getUniqueId()).whenCompleteAsync((ignored, error) -> {
            if (error == null) {
                ok(player, "PIN impostato.");
            } else {
                fail(player, error, "Impossibile salvare il PIN, riprova.");
            }
        }, executors.main());
    }

    private Dialog dialog(String title, String text, String confirm, Consumer<String> onPin) {
        ActionButton confirmButton = ActionButton.builder(Component.text(confirm, NamedTextColor.GREEN))
                .action(DialogAction.customClick((response, audience) -> {
                    String pin = response.getText(INPUT);
                    executors.main().execute(() -> onPin.accept(pin == null ? "" : pin.trim()));
                }, ONCE))
                .build();
        ActionButton cancelButton = ActionButton.builder(Component.text("Annulla")).build();
        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text(title))
                        .body(List.of(DialogBody.plainMessage(Component.text(text))))
                        .inputs(List.of(DialogInput.text(INPUT, Component.text("PIN")).maxLength(8).build()))
                        .build())
                .type(DialogType.confirmation(confirmButton, cancelButton)));
    }

    private static boolean validFormat(Player player, String pin) {
        try {
            PinHasher.validate(pin);
            return true;
        } catch (IllegalArgumentException e) {
            deny(player, "Il PIN deve avere da 4 a 8 cifre.");
            return false;
        }
    }

    /** Errore di un future: le regole hanno un messaggio per il giocatore, il resto si scrive nel log. */
    private void fail(Player player, Throwable error, String fallback) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        if (cause instanceof IllegalStateException) {
            deny(player, "Non puoi farlo: la base è cambiata o non sei più autorizzato.");
        } else {
            logger.log(Level.WARNING, "Errore sul PIN di un Tool Cupboard", cause);
            deny(player, fallback);
        }
    }

    private static void ok(Player player, String message) {
        player.sendMessage(Component.text(message, NamedTextColor.GREEN));
    }

    private static void deny(Player player, String message) {
        player.sendMessage(Component.text(message, NamedTextColor.RED));
    }
}
