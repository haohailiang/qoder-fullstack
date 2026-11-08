package com.ecommerce.user.repository.mapper;

import com.ecommerce.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户表 MyBatis Mapper 接口。
 * 所有 SQL 均定义在 resources/mapper/UserMapper.xml 中，本接口只声明方法签名，
 * 不使用 @Select/@Insert/@Update/@Delete 等注解写 SQL。
 */
@Mapper
public interface UserMapper {

    /** 查询全部用户，按 id 升序 */
    List<User> selectAll();

    /** 按主键查询 */
    User selectById(@Param("id") Long id);

    /** 按认证标识（username）查询 */
    User selectByUsername(@Param("username") String username);

    /** 统计认证标识是否已存在（用于注册查重） */
    int countByUsername(@Param("username") String username);

    /** 统计主键是否存在（用于删除前的存在性校验） */
    int countById(@Param("id") Long id);

    /** 新增用户，主键自增并回填到入参对象的 id 字段 */
    int insert(User user);

    /** 按主键更新用户（不更新 created_by / created_date，仅刷新 last_modified_*） */
    int updateById(User user);

    /** 按主键删除用户 */
    int deleteById(@Param("id") Long id);
}
