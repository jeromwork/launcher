package com.launcher.preset.model

import kotlinx.serialization.Serializable

@Serializable
enum class WizardBehavior { Interactive, AutoApply, InitialDefault }

@Serializable
enum class ComponentStatus { Pending, Applied, Failed, Skipped }

enum class RunMode { Wizard, BootCheck, Single, RemotePush }

@Serializable
enum class Vendor { Xiaomi, Samsung, Huawei, GoogleTV, GenericAndroid, iOS }

@Serializable
enum class Sensitivity { Normal, High, Admin }

@Serializable
enum class TypographyScale { Small, Medium, Large, ExtraLarge }

@Serializable
enum class ShapeStyle { Rounded, Sharp, Mixed }
