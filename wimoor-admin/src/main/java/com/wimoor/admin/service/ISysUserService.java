package com.wimoor.admin.service;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wimoor.admin.pojo.dto.UserDTO;
import com.wimoor.admin.pojo.dto.UserInsertDTO;
import com.wimoor.admin.pojo.dto.UserRegisterInfoDTO;
import com.wimoor.admin.pojo.entity.SysUser;
import com.wimoor.admin.pojo.vo.UserVO;
import com.wimoor.common.user.UserInfo;

public interface ISysUserService  extends IService<SysUser> {
	public SysUser getUserAllByAccount(String account);
	
	public SysUser getUserAllById(String userid);
	
	public SysUser bindOpenId(String openid,String appType, String account, String password);

	public SysUser getUserAllByOpenid(String account);
	
    public Map<String, Object> getUserInfoById(String id) ;
    
	UserInfo convertToUserInfo(SysUser user);

	public int unbindWechat(UserInfo userInfo, String ftype);

	public int unbindAccount(UserInfo userInfo);

	public List<SysUser> findMpUserByOpenid(String openid);

	public List<SysUser> findAppUserByOpenid(String openid,String appType);

	SysUser verifyAccount(String account, String password);

	IPage<UserVO> listQuery(Page<?> page, UserDTO dto);


	public boolean saveUser(UserInsertDTO userDTO, UserInfo operatorUserInfo);

	boolean updateUser(UserInsertDTO userDTO, UserInfo operatorUserInfo);

	public SysUser saveRegister(UserRegisterInfoDTO dto);

	public SysUser changePassword(UserRegisterInfoDTO dto);
	
	public List<Map<String, Object>> findOwnerAll(String shopid,String search) ;

	public Map<String,Object> detail(UserInfo userInfo);
}
