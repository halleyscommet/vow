package us.dingl.VowSMPPlugin.Vows;

/// a vow whose abilities target other players and skip the ones its holder trusts.
/// /vow trust works for every vow that implements this, and keeps them all in sync for players holding several.
public interface TrustingVow {

    TrustList trustList();
}
