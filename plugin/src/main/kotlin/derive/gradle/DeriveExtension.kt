package derive.plugin

import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

public abstract class DeriveExtension @Inject constructor(
    objects: ObjectFactory,
)
