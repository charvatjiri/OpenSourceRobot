package com.gabotapp

sealed class HighLevelCommand {
    data object Stop : HighLevelCommand()
}
