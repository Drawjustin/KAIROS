package io.github.drawjustin.kairos.user.controller

import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/users")
// 사용자 전용 API가 늘어나면 이 컨트롤러 아래로 확장한다.
class UserController
