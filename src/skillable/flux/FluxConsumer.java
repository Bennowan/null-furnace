package skillable.flux;

/** Verbraucher von momentanem Flux (z. B. Skillable Turret). */
public interface FluxConsumer extends FluxMember{
    /** Gewuenschter Durchsatz in diesem Tick (>= 0). */
    float fluxDemand();
    /** Hoeher = wird zuerst bedient. */
    int fluxPriority();
    /** Zugeteilter Flux fuer DIESEN Tick. Wird im naechsten Tick ueberschrieben, nie aufaddiert. */
    void fluxGranted(float granted);
    /** Beitrag zum Instabilitaets-ZIELWERT (0..~0,4), solange dieser Verbraucher tatsaechlich Flux erhaelt (Modus-/Reaktionsrisiko). */
    float riskTarget();
}
