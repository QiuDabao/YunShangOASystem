package com.atguigu.auth.service.impl;

import com.atguigu.auth.mapper.SysMenuMapper;
import com.atguigu.auth.service.SysMenuService;
import com.atguigu.auth.service.SysRoleMenuService;
import com.atguigu.auth.util.MenuHelper;
import com.atguigu.common.execption.GuiguException;
import com.atguigu.model.system.SysMenu;
import com.atguigu.model.system.SysRoleMenu;
import com.atguigu.vo.system.AssignMenuVo;
import com.atguigu.vo.system.MetaVo;
import com.atguigu.vo.system.RouterVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 菜单表 服务实现类
 * </p>
 *
 * @author atguigu
 * @since 2023-04-03
 */
@Service
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements SysMenuService {

    @Resource
    private SysRoleMenuService sysRoleMenuService;
    @Override
    public List<SysMenu> findNodes() {
        //查询所有菜单数据
        List<SysMenu> sysMenusList = baseMapper.selectList(null);
        //构建树形结构
        List<SysMenu> resultList = MenuHelper.buildTree(sysMenusList);
        return resultList;
    }

    @Override
    public void removeMenuById(Long id) {
        //判断还有没有子菜单
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysMenu::getParentId,id);
        Integer count = baseMapper.selectCount(wrapper);
        if (count > 0) {
            throw new GuiguException(201,"菜单还有子菜单");
        }
        baseMapper.deleteById(id);
    }

    @Override
    public List<SysMenu> findSysMenuByRoleId(Long roleId) {
        //查询所有状态为1菜单
        LambdaQueryWrapper<SysMenu> wrapperSysMenu = new LambdaQueryWrapper<>();
        wrapperSysMenu.eq(SysMenu::getStatus,1);
        List<SysMenu> allSysMenuList = baseMapper.selectList(wrapperSysMenu);

        //根据roleId查询 角色id对应的菜单id
        LambdaQueryWrapper<SysRoleMenu> wrapperSysRoleMenu = new LambdaQueryWrapper<>();
        wrapperSysRoleMenu.eq(SysRoleMenu::getRoleId,roleId);
        List<SysRoleMenu> sysRoleMenuList = sysRoleMenuService.list(wrapperSysRoleMenu);

        //根据获取的菜单id,获取对应菜单对象
        //1.先获取所有菜单id
        List<Long> MenuIdList = sysRoleMenuList.stream().map(s -> s.getMenuId()).collect(Collectors.toList());
        //与所有的菜单id进行比较,相同则封装
        allSysMenuList.stream().forEach(item ->{
            if(MenuIdList.contains(item.getId())){
                item.setSelect(true);
            }else {
                item.setSelect(false);
            }
        });
        //返回规定格式的菜单列表
        return MenuHelper.buildTree(allSysMenuList);
    }

    @Override
    public void doAssign(AssignMenuVo assignMenuVo) {
        //根据角色id删除分配数据
        LambdaQueryWrapper<SysRoleMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRoleMenu::getRoleId,assignMenuVo.getRoleId());
        sysRoleMenuService.remove(wrapper);
        //从参数里获取角色新分配菜单id列表
        List<Long> menuIdList = assignMenuVo.getMenuIdList();
        for(Long menuId:menuIdList){
            if (StringUtils.isEmpty(menuId)) {
                continue;
            }
            SysRoleMenu sysRoleMenu = new SysRoleMenu();
            sysRoleMenu.setMenuId(menuId);
            sysRoleMenu.setRoleId(assignMenuVo.getRoleId());
            sysRoleMenuService.save(sysRoleMenu);
        }
    }

    @Override
    public List<RouterVo> findUserMenuListByUserId(Long userId) {
        List<SysMenu> sysMenuList = null;
        //是否是管理员 userId=1
        if (userId.longValue()==1) {
            LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SysMenu::getStatus,1);
            wrapper.orderByAsc(SysMenu::getSortValue);
            sysMenuList = baseMapper.selectList(wrapper);

        }else {
            //多表关联查询:用户角色-角色菜单-菜单
            sysMenuList = baseMapper.findMenuListByUserId(userId);
        }
        //构建出要求的路由数据结构
        List<SysMenu> sysMenusTreeList = MenuHelper.buildTree(sysMenuList);
        List<RouterVo> routerList = this.buildRouter(sysMenusTreeList);

        return routerList;
    }

    @Override
    public List<String> findUserPermsByUserId(Long userId) {
        List<SysMenu> sysMenuList = null;
        //是否为userId为1的管理员
        if (userId.longValue() == 1) {
            LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SysMenu::getStatus,1);
            sysMenuList = baseMapper.selectList(wrapper);
        }else {
            sysMenuList = baseMapper.findMenuListByUserId(userId);
        }
        //从查出来的数据中获取可以操作按钮的list
        List<String> permsList = sysMenuList.stream()
                .filter(item -> item.getType() == 2).
                map(item -> item.getPerms()).
                collect(Collectors.toList());
        return permsList;
    }

    private List<RouterVo> buildRouter(List<SysMenu> menus) {
        List<RouterVo> routers = new ArrayList<>();
        //
        for (SysMenu menu:menus){
            RouterVo router = new RouterVo();
            router.setHidden(false);
            router.setAlwaysShow(false);
            router.setPath(getRouterPath(menu));
            router.setComponent(menu.getComponent());
            router.setMeta(new MetaVo(menu.getName(), menu.getIcon()));
            //下一层
            List<SysMenu> children = menu.getChildren();
            if (menu.getType().intValue() == 1) {
                //加载隐藏路由
                List<SysMenu> hiddenMenuList = children.stream()
                        .filter(item -> StringUtils.isEmpty(item.getComponent()))
                        .collect(Collectors.toList());
                for (SysMenu hiddenMenu:hiddenMenuList){
                    RouterVo hiddenRouter = new RouterVo();
                    router.setHidden(true);
                    router.setAlwaysShow(false);
                    router.setPath(getRouterPath(menu));
                    router.setComponent(menu.getComponent());
                    router.setMeta(new MetaVo(menu.getName(), menu.getIcon()));
                    routers.add(hiddenRouter);
                }
            }else {
                if (!CollectionUtils.isEmpty(children)) {
                    if (children.size() > 0) {
                        router.setAlwaysShow(true);
                    }
                    router.setChildren(buildRouter(children));
                }
            }
            routers.add(router);
        }
        return routers;
    }
    public String getRouterPath(SysMenu menu) {
        String routerPath = "/" + menu.getPath();
        if(menu.getParentId().intValue() != 0) {
            routerPath = menu.getPath();
        }
        return routerPath;
    }
}
