package pokecube.server.mixin.xaeros;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.highlight.HighlighterRegistry;

@Mixin(HighlighterRegistry.class)
public class XareosHighlightRegistry
{
    @Inject(method = "<init>", at = @At(value = "RETURN"))
    public void thutessentials$onConstruct(CallbackInfo ci)
    {
        Object thisObj = this;
        HighlighterRegistry us = (HighlighterRegistry) thisObj;
//        us.register(new HighlightHandler());
    }
}
