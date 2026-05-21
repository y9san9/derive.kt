package derive.plugin

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

private const val VERSION = "1.0.0"

public class DeriveSubplugin : KotlinCompilerPluginSupportPlugin {

    override fun apply(target: Project) {
        target.extensions.create("derive", DeriveExtension::class.java)
    }

    override fun getCompilerPluginId(): String = "derive.compiler"

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = "me.y9san9.derive",
        artifactId = "compiler",
        version = VERSION,
    )

    override fun isApplicable(
        kotlinCompilation: KotlinCompilation<*>,
    ): Boolean = true

    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>,
    ): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project

        project.dependencies.add(
            kotlinCompilation.defaultSourceSet.implementationConfigurationName,
            "me.y9san9.derive:core:$VERSION",
        )

        return project.provider {
            emptyList()
        }
    }
}
