package com.example.neverforgetsaleprice.domain

enum class CheckStatus {
    NeverChecked,
    Success,
    NetworkError,
    HttpError,
    ParseError,
    InvalidUrl
}
