package org.noear.solon.gradle.plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Shihwan
 */
public class SolonNativePlugin extends SolonPlugin {

    @Override
    protected List<PluginApplicationAction> createPluginActions(SinglePublishedArtifact artifact) {
        List<PluginApplicationAction> actions = new ArrayList<>(super.createPluginActions(artifact));
        actions.add(new NativeImagePluginAction());
        return actions;
    }
}
