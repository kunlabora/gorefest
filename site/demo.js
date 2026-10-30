// The interactive demo: one phone, one game day, played through step by step.
// Screens are built from the app's own gorefest.css classes, so the phone looks like the real thing.
// The phone is inert (a picture, not a form); the narration beside it is what a screen reader follows.
(function () {
  'use strict';

  var ICONS = {
    spark: '<path d="M12 3c.6 4.6 3.9 8 8.5 8.5-4.6.6-7.9 3.9-8.5 8.5-.6-4.6-3.9-7.9-8.5-8.5C8.1 11 11.4 7.6 12 3z"/>',
    check: '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
    clock: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>',
    unplayed: '<rect x="4" y="7" width="16" height="12.5" rx="2"/><path d="M4 11h16M9.5 7l1-3h3l1 3"/>',
    meh: '<circle cx="12" cy="12" r="8.5"/><path d="M8.5 15h7"/><path d="M9 9.5h.01M15 9.5h.01" stroke-width="2.6"/>',
    star: '<path d="M12 3.5l2.6 5.3 5.9.9-4.3 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8-4.3-4.1 5.9-.9z"/>',
    users: '<circle cx="9" cy="8.5" r="3.5"/><path d="M2.5 19.5c.6-3.4 3.2-5.5 6.5-5.5s5.9 2.1 6.5 5.5"/><path d="M15.5 5.2a3.5 3.5 0 010 6.6M18 14.4c2 .8 3.2 2.6 3.5 5.1"/>',
    calendar: '<rect x="3.5" y="5" width="17" height="15" rx="2.5"/><path d="M3.5 10h17M8 3v4M16 3v4"/>',
    search: '<circle cx="11" cy="11" r="6.5"/><path d="M16 16l4.5 4.5"/>',
    heart: '<path d="M12 20s-7.5-4.4-7.5-10A4.3 4.3 0 0112 7.3 4.3 4.3 0 0119.5 10c0 5.6-7.5 10-7.5 10z"/>'
  };

  function ic(name) {
    return '<svg class="gf-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + ICONS[name] + '</svg>';
  }

  // Abstract box art, as in the design mockups: no publisher's artwork leaves the app.
  var GAMES = {
    cascadia: { name: 'Cascadia', facts: '30–45 min · light · 1–4', art: '#2f6f5e,#f1d27a,1,hex' },
    spirit: { name: 'Spirit Island', facts: '90–120 min · heavy · 1–4', art: '#1d4a5a,#f0a35e,0.72,peaks' },
    brass: { name: 'Brass: Birmingham', facts: '60–120 min · heavy · 2–4', art: '#4a2e1d,#e0a84a,1,bands' },
    arknova: { name: 'Ark Nova', facts: '90–150 min · heavy · 1–4', art: '#20483a,#e8d9b0,1,hex' },
    heat: { name: 'Heat: Pedal to the Metal', facts: '30–60 min · medium · 1–6', art: '#c73a24,#f6e7c8,1.42,bands' },
    crew: { name: 'The Crew: Mission Deep Sea', facts: '20 min · light · 2–5', art: '#102a4c,#7fd3e6,1,sun' },
    wingspan: { name: 'Wingspan', facts: '40–70 min · medium · 1–5', art: '#e9e1cf,#5a7d3a,0.8,sun' },
    arnak: { name: 'Lost Ruins of Arnak', facts: '60–120 min · medium · 1–4', art: '#6d4a1f,#f3d27a,0.72,peaks' },
    azul: { name: 'Azul', facts: '30–45 min · light · 2–4', art: '#1f4f8f,#f2c14e,1,grid' },
    codenames: { name: 'Codenames', facts: '15 min · party · 4–8', art: '#2b2b2b,#e04a3a,1.3,grid' }
  };

  function art(key, wide) {
    return '<span class="gf-art' + (wide ? ' gf-art--wide' : '') + '"><img alt="" data-ph="' + GAMES[key].art + '"></span>';
  }

  var PEOPLE = [['Anna', 'AN', 0], ['Bram', 'BR', 1], ['Kim', 'KI', 2], ['Jonas', 'JO', 3], ['Sofie', 'SO', 1]];

  function avatar(p, size) {
    return '<span class="gf-avatar' + (size ? ' gf-avatar--' + size : '') + '" data-hue="' + p[2] + '">' + p[1] + '</span>';
  }

  function appbar(title, action) {
    return '<header class="gf-appbar"><span class="gf-appbar__title">' + title + '</span>' +
      (action ? '<span class="gf-btn gf-btn--ghost gf-btn--sm">' + action + '</span>' : '') + '</header>';
  }

  function pick(key, letter) {
    var g = GAMES[key];
    return '<span class="gf-pick" data-demo="' + letter + '"><span class="gf-pick__key">' + letter + '</span>' + art(key) +
      '<span class="gf-pick__name">' + g.name + '</span><span class="gf-pick__facts">' + g.facts + '</span><span class="gf-spin"></span></span>';
  }

  function duel(a, b) {
    return '<div class="gf-duel gf-deal">' + pick(a, 'A') + '<span class="gf-duel__or">or</span>' + pick(b, 'B') + '</div>';
  }

  function progress(done, total) {
    var pips = '';
    for (var i = 0; i < total; i++) pips += '<span class="gf-progress__pip' + (i < done ? ' gf-progress__pip--done' : '') + '"></span>';
    return '<div class="gf-progress"><div class="gf-progress__track">' + pips + '</div><span class="gf-progress__count">' + (done + 1) + ' / ' + total + '</span></div>';
  }

  function ranked(rank, key, meta, reason, large) {
    var g = GAMES[key];
    return '<article class="gf-game' + (large ? ' gf-game--large' : '') + '"><span class="gf-game__rank">' + rank + '</span>' + art(key, large) +
      '<div class="gf-game__body"><h3 class="gf-game__name">' + g.name + '</h3><p class="gf-game__meta">' + meta + '</p>' +
      '<p class="gf-game__reason">' + ic('spark') + reason + '</p></div></article>';
  }

  function chips(names, checked) {
    return '<div class="gf-chips">' + PEOPLE.filter(function (p) { return names.indexOf(p[0]) >= 0; }).map(function (p) {
      return '<label class="gf-chip" data-demo="chip-' + p[0] + '"><input type="checkbox"' + (checked.indexOf(p[0]) >= 0 ? ' checked' : '') + '>' +
        avatar(p, 'sm') + '<span class="gf-chip__check">' + ic('check') + '</span>' + p[0] + '</label>';
    }).join('') + '</div>';
  }

  function seg(options, selected) {
    return '<div class="gf-seg" style="border:1px solid var(--line)">' + options.map(function (o) {
      return '<label data-demo="seg-' + o + '"><input type="radio" name="seg"' + (o === selected ? ' checked' : '') + '>' + o + '</label>';
    }).join('') + '</div>';
  }

  function toggleRow(key, on) {
    var g = GAMES[key];
    return '<li><div class="gf-game gf-game--compact">' + art(key) + '<div class="gf-game__body"><h3 class="gf-game__name">' + g.name +
      '</h3><p class="gf-game__meta">' + g.facts + '</p></div><input class="gf-switch" type="checkbox" role="switch" data-demo="bring-' + key + '"' + (on ? ' checked' : '') + '></div></li>';
  }

  function dock(label, demo, extra) {
    return '<div class="gf-dock"><span class="gf-btn gf-btn--primary gf-btn--lg gf-btn--block" data-demo="' + demo + '">' + label + '</span>' + (extra || '') + '</div>';
  }

  // ---------- The steps ----------

  var STEPS = [
    {
      kicker: 'The invite',
      title: 'One link in the group chat',
      text: 'Anna hosts Gorefest #7 and shares one link where the group already talks. Kim sees who\'s coming and how many games are on the shelf before signing up for anything.',
      screen: function () {
        return '<main class="gf-body" style="padding-top:var(--space-5)">' +
          '<div style="display:flex;align-items:center;gap:var(--space-3)">' + avatar(PEOPLE[0], 'lg') + '<p style="margin:0" class="gf-muted"><strong style="color:var(--ink)">Anna</strong> invites you to a game day</p></div>' +
          '<p class="gf-hero">Gorefest #7</p>' +
          '<ul class="gf-facts"><li>' + ic('calendar') + 'Saturday 12 October · from 11:00</li><li>' + ic('users') + '<span class="gf-avatars">' +
          PEOPLE.slice(1).map(function (p) { return avatar(p, 'sm'); }).join('') + '</span>7 players going</li></ul>' +
          '<div class="gf-section" style="margin-top:var(--space-2)"><p class="gf-h2">On the shelf</p><div style="display:flex;align-items:center;gap:var(--space-4)"><div class="gf-fan">' +
          ['brass', 'heat', 'spirit', 'azul'].map(function (k, i) { return art(k).replace('class="gf-art"', 'class="gf-art" style="--r:' + [-6, 3, -2, 5][i] + 'deg"'); }).join('') +
          '</div><p style="margin:0;font:800 30px/30px var(--font-display)">23<span class="gf-muted" style="display:block;font:400 14px/20px var(--font-sans)">games so far</span></p></div></div>' +
          '<p class="gf-small gf-muted" style="margin:0">Answer a few "which of these two?" questions and the app picks the games you\'ll enjoy most on the day.</p></main>' +
          dock('Join Gorefest #7', 'join', '<p class="gf-hint" style="text-align:center">Just your email. No password, no app to install.</p>');
      },
      play: function (d) { return d.tap('join'); }
    },
    {
      kicker: 'Sign in',
      title: 'No password, no app',
      text: 'An email with a link and a 6-digit code. The code also works in the in-app browsers of WhatsApp and Signal, where magic links tend to break.',
      screen: function () {
        return appbar('Gorefest #7') + '<main class="gf-body"><div class="gf-stack"><p class="gf-h1">What\'s your email?</p><p class="gf-muted" style="margin:0">We\'ll send a link and a 6-digit code. No password to remember.</p></div>' +
          '<div class="gf-field"><span class="gf-label">Email</span><input class="gf-input" data-demo="email" placeholder="you@example.com"></div>' +
          '<div class="gf-field" data-demo="codefield" hidden><span class="gf-label">6-digit code</span><input class="gf-input gf-code" data-demo="code" placeholder="······"><p class="gf-hint">Opened this from WhatsApp or Signal? The code is the quicker route.</p></div></main>' +
          dock('Send me a code', 'send');
      },
      play: function (d) {
        return d.type('email', 'kim@example.com')
          .then(function () { return d.tap('send'); })
          .then(function () {
            d.el('codefield').hidden = false;
            d.el('send').textContent = 'Let me in';
            return d.type('code', '391740');
          })
          .then(function () { return d.tap('send'); });
      }
    },
    {
      kicker: 'Taste round',
      title: 'Which would you rather play?',
      text: 'Rating forty games is a chore. Choosing between two is easy. A dozen quick pairs are enough to learn Kim\'s taste: which games look appealing, and which were fun once played.',
      screen: function () {
        return appbar('Gorefest #7 · Taste round', 'Done for now') +
          '<main class="gf-body gf-wizard"><div data-demo="progress">' + progress(4, 12) + '</div>' +
          '<div class="gf-stack"><span class="gf-tag gf-tag--accent" data-demo="tag" style="align-self:flex-start">' + ic('spark') + 'Unplayed pair</span><p class="gf-h1" data-demo="question">Which would you rather try?</p></div>' +
          '<div class="gf-wizard__form" data-demo="duel">' + duel('cascadia', 'spirit') + '</div>' +
          '<div class="gf-answers"><span class="gf-answer">' + ic('unplayed') + 'Haven\'t played</span><span class="gf-answer">' + ic('meh') + 'Both meh</span>' +
          '<span class="gf-answer gf-answer--great" data-demo="great">' + ic('star') + 'Both great</span></div></main>';
      },
      play: function (d) {
        function next(done, a, b, played) {
          return function () {
            d.el('progress').innerHTML = progress(done, 12);
            d.el('duel').innerHTML = duel(a, b);
            d.el('tag').className = 'gf-tag ' + (played ? 'gf-tag--olive' : 'gf-tag--accent');
            d.el('tag').innerHTML = played ? ic('check') + 'You\'ve played both' : ic('spark') + 'Unplayed pair';
            d.el('question').textContent = played ? 'Which did you enjoy more?' : 'Which would you rather try?';
            d.paint();
            return d.wait(900);
          };
        }
        return d.wait(700)
          .then(function () { return d.tap('A', true); })
          .then(next(5, 'brass', 'arknova', true))
          .then(function () { return d.tap('great'); })
          .then(next(6, 'heat', 'crew', false))
          .then(function () { return d.tap('A', true); })
          .then(next(11, 'wingspan', 'arnak', false))
          .then(function () { return d.tap('B', true); });
      }
    },
    {
      kicker: 'The payoff',
      title: 'Your likely favourites today',
      text: 'Right away, Kim sees the games on this shelf most likely to land, each with a reason in plain words. The answers also carry over to games Kim has never seen, through what they have in common: mechanics, weight, length.',
      screen: function () {
        return '<main class="gf-body" style="padding-top:var(--space-6)"><div class="gf-stack"><p class="gf-kicker">Round done · 12 answers</p><p class="gf-hero">Your likely favourites today</p></div>' +
          '<div class="gf-podium" data-demo="podium">' +
          ranked(1, 'arknova', '90–150 min · heavy · 1–4', 'Big engine, lots of cards: right in your wheelhouse.', true) +
          ranked(2, 'brass', '60–120 min · heavy · 2–4', 'You loved heavy economic games so far.') +
          ranked(3, 'cascadia', '30–45 min · light · 1–4', 'Short and calm, for when your brain needs a break.') + '</div>' +
          '<div class="gf-notice gf-notice--good">' + avatar(PEOPLE[1], 'sm') + '<p><strong>You and Bram</strong> both lean towards heavy euros. Grab a table together.</p></div></main>' +
          dock('Open Gorefest #7', 'open');
      },
      play: function (d) { return d.wait(1600).then(function () { return d.tap('open'); }); }
    },
    {
      kicker: 'The shelf',
      title: 'Tick what you\'re bringing',
      text: 'Kim\'s collection comes from a linked BoardGameGeek account. Bringing a game is one switch, so the shelf fills up before the day and the host can spot the gaps, like "nothing plays well at 6".',
      screen: function () {
        return appbar('Gorefest #7') + '<main class="gf-body"><div class="gf-stack"><p class="gf-h1">What are you bringing?</p><p class="gf-muted" style="margin:0" data-demo="count">From your collection. 2 ticked.</p></div>' +
          '<div class="gf-search">' + ic('search') + '<input class="gf-input" placeholder="Search your collection or BGG"></div>' +
          '<ul class="gf-list">' + toggleRow('arknova', true) + toggleRow('crew', true) + toggleRow('wingspan', false) + toggleRow('codenames', false) + toggleRow('arnak', false) + '</ul></main>' +
          dock('Done: 2 games', 'done');
      },
      play: function (d) {
        return d.tap('bring-wingspan').then(function () {
          d.el('bring-wingspan').checked = true;
          d.el('count').textContent = 'From your collection. 3 ticked.';
          d.el('done').textContent = 'Done: 3 games';
          return d.wait(600);
        }).then(function () { return d.tap('done'); });
      }
    },
    {
      kicker: 'On the day',
      title: 'Who\'s at this table?',
      text: 'It\'s Saturday. Four people are free, there\'s an hour and a half before dinner, and nobody wants to be the one who picks. Tick who\'s playing and how long you have.',
      screen: function () {
        return appbar('Gorefest #7 · Pick a game') + '<main class="gf-body"><div class="gf-stack"><p class="gf-h1">Who\'s at the table?</p></div>' +
          chips(['Anna', 'Bram', 'Kim', 'Jonas', 'Sofie'], ['Kim']) +
          '<div class="gf-section"><p class="gf-h2">How long have you got?</p>' + seg(['45m', '1h', '90m', '2h+'], '1h') + '</div></main>' +
          dock('Show suggestions', 'show');
      },
      play: function (d) {
        var tick = function (name) { return function () { return d.tap('chip-' + name).then(function () { d.el('chip-' + name).querySelector('input').checked = true; }); }; };
        return d.wait(500).then(tick('Anna')).then(tick('Bram')).then(tick('Jonas'))
          .then(function () { return d.tap('seg-90m'); })
          .then(function () { d.el('seg-90m').querySelector('input').checked = true; return d.tap('show'); });
      }
    },
    {
      kicker: 'Suggestions',
      title: 'Three games, each with a reason',
      text: 'Only games that fit four players and 90 minutes. Ranked by how much the whole table is likely to enjoy them, not just the loudest player, with a bonus for games nobody has played and games someone really wants to play.',
      screen: function () {
        return appbar('Gorefest #7 · Pick a game') + '<main class="gf-body"><div class="gf-tablebar"><span class="gf-avatars">' +
          PEOPLE.slice(0, 4).map(function (p) { return avatar(p, 'sm'); }).join('') +
          '</span><p class="gf-tablebar__text" style="margin:0">4 players · 90 min<span>Anna, Bram, Kim, Jonas</span></p><span class="gf-btn gf-btn--secondary gf-btn--sm">Change</span></div>' +
          '<section class="gf-section"><p class="gf-h2">Play this</p>' +
          ranked(1, 'heat', ic('clock') + '~55 min for the 4 of you', 'Nobody\'s played it and everyone\'s likely to enjoy it.', true) +
          ranked(2, 'arnak', ic('clock') + '~85 min for 4', 'Kim brought it and has been itching to play.') +
          ranked(3, 'azul', ic('clock') + '~35 min for 4', 'Safe bet: all four of you rated it highly.') + '</section></main>';
      },
      play: function (d) { return d.wait(2600); }
    },
    {
      kicker: 'Checkpoint',
      title: 'What did you play?',
      text: 'At lunch and in the evening the app asks what was played, by whom and for how long. It\'s three taps, because the likeliest answers are already filled in. That keeps the time estimates honest for this group.',
      screen: function () {
        return appbar('Gorefest #7', 'Skip') + '<main class="gf-body"><div class="gf-stack"><p class="gf-kicker">Lunch checkpoint</p><p class="gf-h1">What did you play?</p></div>' +
          '<div class="gf-tiles">' + ['heat', 'cascadia', 'crew'].map(function (k) {
            return '<label class="gf-tile" data-demo="tile-' + k + '"><input type="radio" name="game">' + art(k) + '<span class="gf-tile__name">' + GAMES[k].name + '</span></label>';
          }).join('') + '</div>' +
          '<div class="gf-section"><p class="gf-h2">Who played?</p>' + chips(['Anna', 'Bram', 'Kim', 'Jonas'], ['Anna', 'Bram', 'Kim', 'Jonas']) + '</div>' +
          '<div class="gf-section"><p class="gf-h2">Roughly how long?</p>' + seg(['30m', '45m', '1h', '90m'], '') + '</div></main>' + dock('Next', 'next');
      },
      play: function (d) {
        return d.tap('tile-heat').then(function () { d.el('tile-heat').querySelector('input').checked = true; return d.tap('seg-1h'); })
          .then(function () { d.el('seg-1h').querySelector('input').checked = true; return d.tap('next'); });
      }
    },
    {
      kicker: 'One more answer',
      title: 'Every game played sharpens the next pick',
      text: 'One comparison after each play: did you enjoy it more than the last one? Over a day, and over many game days, the suggestions keep getting closer to what your group actually loves.',
      screen: function () {
        return appbar('Gorefest #7') + '<main class="gf-body gf-wizard"><div class="gf-notice gf-notice--good">' + ic('check') + '<p>Logged Heat: Pedal to the Metal: 4 players, 1 h.</p></div>' +
          '<div class="gf-stack"><p class="gf-kicker">One quick question</p><p class="gf-h1">Which did you enjoy more?</p></div>' +
          '<div class="gf-wizard__form">' + duel('heat', 'cascadia') + '</div></main>';
      },
      play: function (d) { return d.wait(900).then(function () { return d.tap('A', true); }).then(function () { return d.wait(400); }); }
    }
  ];

  // ---------- Placeholder box art (same generator as the mockups) ----------

  function paintArt(root) {
    root.querySelectorAll('img[data-ph]:not([src])').forEach(function (img) {
      var p = img.getAttribute('data-ph').split(','), bg = p[0], fg = p[1], r = parseFloat(p[2]), m = p[3];
      var W = 300, H = Math.round(300 / r), s = '', x, y, i;
      if (m === 'sun') s = '<circle cx="' + W * .5 + '" cy="' + H * .45 + '" r="' + Math.min(W, H) * .28 + '" fill="' + fg + '"/><rect y="' + H * .78 + '" width="' + W + '" height="' + H * .22 + '" fill="' + fg + '" opacity=".35"/>';
      if (m === 'bands') for (i = 0; i < 5; i++) s += '<rect x="0" y="' + (H * .18 + i * H * .13) + '" width="' + W + '" height="' + H * .06 + '" fill="' + fg + '" opacity="' + (1 - i * .16) + '"/>';
      if (m === 'peaks') s = '<path d="M0 ' + H + ' L' + W * .3 + ' ' + H * .35 + ' L' + W * .5 + ' ' + H * .6 + ' L' + W * .72 + ' ' + H * .22 + ' L' + W + ' ' + H + 'Z" fill="' + fg + '"/><circle cx="' + W * .78 + '" cy="' + H * .12 + '" r="' + W * .06 + '" fill="' + fg + '"/>';
      if (m === 'hex') for (y = 0; y < 3; y++) for (x = 0; x < 3; x++) {
        var cx = W * (.25 + x * .25 + (y % 2) * .125), cy = H * (.28 + y * .22), q = W * .1;
        s += '<polygon points="' + [0, 1, 2, 3, 4, 5].map(function (k) { var a = Math.PI / 3 * k + Math.PI / 6; return (cx + q * Math.cos(a)) + ',' + (cy + q * Math.sin(a)); }).join(' ') + '" fill="' + fg + '" opacity="' + (.4 + ((x + y) % 3) * .3) + '"/>';
      }
      if (m === 'grid') for (y = 0; y < 4; y++) for (x = 0; x < 4; x++) if ((x + y) % 2 === 0 || x === y) s += '<rect x="' + (W * .12 + x * W * .2) + '" y="' + (H * .12 + y * H * .2) + '" width="' + W * .16 + '" height="' + H * .16 + '" rx="6" fill="' + fg + '" opacity="' + (x === y ? 1 : .55) + '"/>';
      var u = 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ' + W + ' ' + H + '"><rect width="100%" height="100%" fill="' + bg + '"/>' + s + '</svg>');
      img.src = u;
      img.parentNode.style.setProperty('--art', 'url("' + u + '")');
    });
  }

  // ---------- The player ----------

  paintArt(document);

  var root = document.getElementById('demo');
  if (!root) return;
  var screen = root.querySelector('[data-phone-screen]');
  var finger = root.querySelector('.demo-finger');
  var kicker = root.querySelector('[data-step-kicker]');
  var title = root.querySelector('[data-step-title]');
  var text = root.querySelector('[data-step-text]');
  var back = root.querySelector('[data-back]');
  var next = root.querySelector('[data-next]');
  var play = root.querySelector('[data-play]');
  var dots = root.querySelector('[data-dots]');
  var reduced = window.matchMedia('(prefers-reduced-motion: reduce)');

  var current = 0, run = 0, autoplay = false;

  function Stopped() {}

  // Everything a step's script can do. Each call checks that the step is still the one on screen.
  function director(id) {
    var speed = reduced.matches ? 0 : 1;
    var d = {
      el: function (name) { return screen.querySelector('[data-demo="' + name + '"]'); },
      paint: function () { paintArt(screen); },
      wait: function (ms) {
        return new Promise(function (resolve, reject) {
          setTimeout(function () { if (id === run) resolve(); else reject(new Stopped()); }, ms * speed);
        });
      },
      tap: function (name, lock) {
        var target = d.el(name);
        if (!target) return d.wait(0);
        var phone = screen.getBoundingClientRect(), box = target.getBoundingClientRect();
        finger.style.transform = 'translate(' + (box.left - phone.left + box.width / 2) + 'px,' + (box.top - phone.top + box.height / 2) + 'px)';
        finger.classList.add('is-on');
        return d.wait(650).then(function () {
          finger.classList.add('is-pressing');
          target.classList.add('demo-pressed');
          if (lock) target.setAttribute('aria-busy', 'true');
          return d.wait(450);
        }).then(function () {
          finger.classList.remove('is-pressing');
          target.classList.remove('demo-pressed');
          return d.wait(250);
        });
      },
      type: function (name, value) {
        var input = d.el(name), i = 0;
        var tap = d.tap(name);
        function letter() {
          if (i >= value.length) return d.wait(300);
          input.value = value.slice(0, ++i);
          return d.wait(70).then(letter);
        }
        return tap.then(letter);
      }
    };
    return d;
  }

  function show(index, still) {
    current = Math.max(0, Math.min(STEPS.length - 1, index));
    var id = ++run, step = STEPS[current];
    screen.innerHTML = '<div class="gf-screen">' + step.screen() + '</div>';
    paintArt(screen);
    finger.classList.remove('is-on', 'is-pressing');
    kicker.textContent = (current + 1) + ' of ' + STEPS.length + ' · ' + step.kicker;
    title.textContent = step.title;
    text.textContent = step.text;
    back.disabled = current === 0;
    next.textContent = current === STEPS.length - 1 ? 'Start over' : 'Next';
    dots.querySelectorAll('button').forEach(function (b, i) {
      if (i === current) b.setAttribute('aria-current', 'step'); else b.removeAttribute('aria-current');
    });
    if (still) return;
    step.play(director(id)).then(function () {
      finger.classList.remove('is-on');
      if (autoplay && id === run) {
        if (current === STEPS.length - 1) setAutoplay(false);
        else setTimeout(function () { if (id === run && autoplay) show(current + 1); }, reduced.matches ? 6000 : 1800);
      }
    }, function (e) { if (!(e instanceof Stopped)) throw e; });
  }

  function setAutoplay(on) {
    autoplay = on;
    play.textContent = on ? 'Pause' : 'Play';
  }

  STEPS.forEach(function (step, i) {
    var b = document.createElement('button');
    b.type = 'button';
    b.className = 'demo-dot';
    b.setAttribute('aria-label', 'Step ' + (i + 1) + ': ' + step.kicker);
    b.addEventListener('click', function () { setAutoplay(false); show(i); });
    dots.appendChild(b);
  });

  back.addEventListener('click', function () { setAutoplay(false); show(current - 1); });
  next.addEventListener('click', function () { setAutoplay(false); show(current === STEPS.length - 1 ? 0 : current + 1); });
  play.addEventListener('click', function () {
    var on = !autoplay;
    setAutoplay(on);
    if (on) show(current === STEPS.length - 1 ? 0 : current);
  });
  document.querySelectorAll('[data-start-demo]').forEach(function (a) {
    a.addEventListener('click', function () { setAutoplay(true); show(0); });
  });

  // Start the first step only once the demo scrolls into view, so its animation isn't missed.
  show(0, true);
  if ('IntersectionObserver' in window) {
    var seen = new IntersectionObserver(function (entries) {
      if (entries.some(function (e) { return e.isIntersecting; })) { seen.disconnect(); if (current === 0 && !autoplay) show(0); }
    }, { threshold: .5 });
    seen.observe(root.querySelector('.demo-phone'));
  }
})();
