/**
 * 本文件定义登录与注册成功后的返回内容。
 */
package com.wjfz.bugloop.auth.vo;

import com.wjfz.bugloop.user.vo.UserVO;

/**
 * 登录结果。
 *
 * @param token 登录凭证，前端通过 Authorization 请求头携带
 * @param user 当前用户信息
 */
public record LoginResponse(String token, UserVO user) {
}
