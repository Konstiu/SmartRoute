import { AnimationController } from '@ionic/angular';

export const mapModalEnter = (baseEl: HTMLElement) => {
  const backdrop = baseEl.querySelector('ion-backdrop')!;
  const wrapper = baseEl.querySelector('.modal-wrapper') as HTMLElement;

  const animationCtrl = new AnimationController();

  const backdropAnimation = animationCtrl
    .create()
    .addElement(backdrop)
    .fromTo('opacity', '0', '0.4');

  const wrapperAnimation = animationCtrl
    .create()
    .addElement(wrapper)
    .fromTo('transform', 'scale(0.95)', 'scale(1)')
    .fromTo('opacity', '0', '1');

  return animationCtrl
    .create()
    .addAnimation([backdropAnimation, wrapperAnimation])
    .duration(250)
    .easing('ease-out');
};

export const mapModalLeave = (baseEl: HTMLElement) => {
  const backdrop = baseEl.querySelector('ion-backdrop')!;
  const wrapper = baseEl.querySelector('.modal-wrapper') as HTMLElement;

  const animationCtrl = new AnimationController();

  return animationCtrl
    .create()
    .addElement(wrapper)
    .fromTo('transform', 'scale(1)', 'scale(0.95)')
    .fromTo('opacity', '1', '0')
    .duration(200)
    .easing('ease-in');
};
